---
phase: 09-healthkit-import
reviewed: 2026-06-09T00:00:00Z
depth: standard
files_reviewed: 21
files_reviewed_list:
  - Sources/EndoApp/Health/HealthDomain.swift
  - Sources/EndoApp/Health/ImportedSampleValue.swift
  - Sources/EndoApp/Health/HealthDataReading.swift
  - Sources/EndoApp/Health/HealthSampleMapping.swift
  - Sources/EndoApp/Health/HealthAnchorStore.swift
  - Sources/EndoApp/Health/FixtureHealthDataReading.swift
  - Sources/EndoApp/Health/HealthImportService.swift
  - Sources/EndoApp/Health/SystemHealthDataReading.swift
  - Sources/EndoApp/Health/HealthConnectCopy.swift
  - Sources/EndoApp/Persistence/Models/ImportedSample.swift
  - Sources/EndoApp/Persistence/SchemaV6.swift
  - Sources/EndoApp/Persistence/MigrationPlan.swift
  - Sources/EndoApp/Persistence/Repository.swift
  - Sources/EndoApp/App/AppDependencies.swift
  - Sources/EndoApp/App/EndoAppApp.swift
  - Sources/EndoApp/Features/Health/HealthRationaleView.swift
  - Sources/EndoApp/Features/Health/HealthConnectSection.swift
  - Sources/EndoApp/Features/Health/HealthSourceBadge.swift
  - Sources/EndoApp/Features/Settings/SettingsView.swift
  - Sources/EndoApp/Features/Home/HomeView.swift
  - project.yml
findings:
  critical: 2
  warning: 5
  info: 4
  total: 11
status: issues_found
---

# Phase 9: Code Review Report

**Reviewed:** 2026-06-09
**Depth:** standard
**Files Reviewed:** 21
**Status:** issues_found

## Summary

Phase 9 adds a read-only HealthKit import path: a Sendable seam (`HealthDataReading`), a `@globalActor`-isolated real reader, pure mapping tables, an actor orchestrator, additive CloudKit-safe persistence (`ImportedSample` / SchemaV6 / lightweight V5→V6), the manual-wins/nil-honest merge in `Repository`, anchor persistence outside CloudKit, and the connect/rationale/badge/refresh UI.

The Swift 6 concurrency boundary is handled correctly: `HKSample`/`HKHealthStore` never escape `@HealthStoreActor`, only Sendable value structs + the opaque `HKQueryAnchor` cross the seam, the anchored-query continuation resumes exactly once on each path, and the read-only posture (`toShare: []`, share-only Info.plist key, empty `.access` entitlement) is intact. The SC3 honesty invariants (no coercion of absence to 0, consent-gating, no health-content logging) are largely respected.

However there is one **make-or-break correctness defect** in the sleep merge (CR-01) that silently mis-reports nightly sleep, and a **deletion-vs-reinsert data-loss ordering bug** in the persist unit (CR-02). Both directly affect the honesty/correctness contract the phase exists to uphold. Several warnings concern provenance/value drift between the source-badge read and the merged value, and the imported-activity-minutes graft.

## Critical Issues

### CR-01: Sleep merge overwrites instead of summing — nightly sleep is mis-reported

**File:** `Sources/EndoApp/Health/SystemHealthDataReading.swift:128-145`, `Sources/EndoApp/Persistence/Repository.swift:774-792` (`ImportedSignals.init`)

**Issue:** Apple Health (especially Apple Watch) records a single night as **many** `HKCategoryType(.sleepAnalysis)` samples — one segment per stage/awake transition (e.g. 6–30 short asleepCore/asleepREM/asleepDeep segments). `SystemHealthDataReading.value(from:)` maps **each segment independently** to its own `ImportedSampleValue` carrying only that one segment's `hours` (`nightlyAsleepHours([segment])`) and a quality band derived from that single fragment. Each becomes a distinct `ImportedSample` row (distinct `hkUUID`).

Then `ImportedSignals.init(rows:)` does, for every `.sleep` row:
```swift
case .sleep:
    sleepHours = row.sleepHours          // OVERWRITE, not +=
    sleepQuality = EndoInsights.SleepQuality(raw: row.sleepQualityRaw)
```
This is **last-writer-wins**, not a sum. The merged `sleepHours` for the day ends up being a single ~5–40 minute fragment, and `sleepQuality` is computed from that fragment (almost always `.poor`). A real 7.5h night surfaces as e.g. `0.4h / poor`.

The source comments explicitly claim the opposite: `SystemHealthDataReading.swift:132-133` says "the merge sums them," and `Repository.swift:773` says "Last-writer-wins per domain." There is no summing anywhere (confirmed: no `+=`/`reduce` over `sleepHours` in `Repository`). This is a silent data-correctness failure that mis-represents the user's own health data — the exact SC3 honesty class this phase is meant to protect.

**Fix:** Aggregate sleep per day rather than per segment. Either (a) accumulate in the merge:
```swift
case .sleep:
    sleepHours = (sleepHours ?? 0) + (row.sleepHours ?? 0)
    // recompute the band from the running total, not per-row:
    sleepQuality = sleepHours.map { HealthSampleMapping.sleepQuality(forAsleepHours: $0) }
```
(note: this requires `ImportedSignals` to call the mapping, and the per-row `sleepQualityRaw` becomes advisory). Or (b) preferred: roll segments up to a per-day total inside the reader's `pull` before emitting one `.sleep` value per day, so the dedupe-by-uuid store does not fragment a night across rows. Whichever path is chosen, add a unit test that feeds N segments for one night and asserts the merged `sleepHours` equals the sum and the band matches the total. Correct the "sums them" comments to match the actual behavior.

### CR-02: replaceOrInsertImported deletes-then-reinserts but cannot reconstruct a same-pull delete+re-add — and the per-value save defeats the delete-first ordering

**File:** `Sources/EndoApp/Persistence/Repository.swift:714-728`

**Issue:** Two coupled problems in the single persist unit the import service calls:

1. The method applies `deletedUUIDs` first (delete rows by `hkSampleUUID`), then upserts `values`. The stated intent (line 716) is "a same-pull delete-then-reinsert of the same UUID nets correctly." But HealthKit's anchored query reports a changed sample as a deletion of the old UUID **plus a new sample with a new UUID** — it does not re-deliver the same UUID in both `deleted` and `samples`. So if a sample legitimately appears in *both* lists for the same UUID (which the dedupe contract treats as the stable key), the delete-first ordering **drops the row the same pull intended to keep**: the upsert re-creates it, so for a true same-UUID delete+re-add it nets to "present" — but the comment's reasoning is inverted from HealthKit's actual semantics, and the order is only safe by luck. More importantly:

2. `upsertImportedSample` (called in the loop) **calls `context.save()` itself** (line 700), and `replaceOrInsertImported` then calls `context.save()` again at line 727. The deletions are saved together with the first value's upsert, not atomically with the whole batch. If the loop throws partway (e.g. a malformed value), the deletions and a prefix of the upserts are already committed while later values are lost — the cursor is then NOT advanced by `HealthImportService` (it treats the throw as "contributed nothing"), so the next pull re-delivers, but the store is left in a partially-applied state with no transactional boundary. The "in one consent-gated unit" docstring (Repository.swift:156) overstates the atomicity actually provided.

**Fix:** Make the batch a single transaction: do all deletions and all upserts against the context, then call `context.save()` exactly once at the end. Stop calling `context.save()` inside `upsertImportedSample` when it is driven by the batch (extract a non-saving `upsertImportedSample(_:save:)` or an internal `apply`-only helper, and save once in `replaceOrInsertImported`). Add a test that injects a throwing value mid-batch and asserts the store is unchanged (all-or-nothing), and a test for the same-UUID delete+re-add net result.

## Warnings

### WR-01: Imported workout minutes can be grafted onto a manual activity that has a different type but only the manual type wins for `daySnapshotSources`

**File:** `Sources/EndoApp/Persistence/Repository.swift:843-845` vs `Sources/EndoApp/Persistence/Repository.swift:606-615`

**Issue:** In `makeDaySnapshot`, imported activity fills BOTH type and minutes only when `f.activityType == nil` (good — prevents grafting minutes onto a manual-typed activity). But the provenance read `daySnapshotSources(on:)` computes `activity` source purely from "manual activity row exists" vs "imported activity present," using a different predicate (`imported.activityType != nil || imported.activityMinutes != nil`) than the merge's `useImportedActivity` gate. If a manual activity has a type but nil minutes, and an imported workout has minutes, the merge keeps the manual type+minutes (manual wins), yet the badge logic could still report `.appleHealth` is "present" — but `source()` returns `.manual` first, so the badge is correct here. The drift is latent: the two code paths encode the manual-wins decision independently and can diverge in a future edit. The merge already documents this as "the SINGLE merge decision is the source of truth" (line 593) but in fact re-derives it.

**Fix:** Have `daySnapshotSources(on:)` consume the SAME `MergeResolution`/`makeDaySnapshot` logic (or extract the per-field win decision into one shared function returning both value and source) so value and provenance can never drift. At minimum, mirror the `useImportedActivity` gate in the activity source computation.

### WR-02: Sleep that crosses midnight is bucketed entirely into the start day, fragmenting a single night

**File:** `Sources/EndoApp/Health/SystemHealthDataReading.swift:115,128-145`

**Issue:** `day = Calendar.current.startOfDay(for: sample.startDate)`. A sleep segment that begins at 23:40 and one that begins at 00:20 the same night land in two different day buckets. Combined with CR-01 (no summing), a night is both split across days and reduced to fragments. Even after CR-01 is fixed, segment-level start-of-day bucketing will split a normal night across two calendar days. Apple's own convention attributes a night's sleep to the wake day.

**Fix:** Decide and document the night-attribution rule (typically: attribute the whole sleep session to the calendar day it ENDS, or to a noon-to-noon "sleep day"). Roll the night's segments up under one day key in the reader before emitting, then aggregate per CR-01. Add a midnight-crossing test.

### WR-03: `HealthConnectSection` marks "decided" on ANY rationale dismissal, including "Not now"

**File:** `Sources/EndoApp/Features/Health/HealthConnectSection.swift:41-48`

**Issue:** The `.sheet(onDismiss:)` sets `hasDecided = true` unconditionally — including when the user tapped "Not now" without ever proceeding to the system sheet. After that, the connect button deep-links to the system Settings app (D-08 "previously decided" path) even though the user never granted or denied anything and Apple's sheet never appeared. A user who taps Connect, reconsiders, and taps "Not now" is then permanently routed to Settings.app with no way to reach the in-app rationale again. The code comment acknowledges this is a deliberate simplification, but it degrades the first-run UX and contradicts D-08's "no nag, reads can't be re-requested" rationale (nothing was requested).

**Fix:** Flip `hasDecided` only on the "Continue" path (e.g. set a flag in `HealthRationaleView.proceed()` before dismiss, or pass a completion to the sheet), not on plain "Not now". "Not now" should leave the affordance in its first-run state.

### WR-04: Multiple workouts on the same day collapse to one (last-writer-wins) with no aggregation

**File:** `Sources/EndoApp/Persistence/Repository.swift:783-785` (`ImportedSignals.init` `.workouts` case)

**Issue:** Like sleep, each workout becomes its own `ImportedSample` row. For a day with two workouts (e.g. a walk and a strength session) `ImportedSignals.init` overwrites `activityType`/`activityMinutes` with whichever row iterates last (fetch order is unspecified — non-deterministic). The day's imported activity is therefore arbitrary, and minutes are a single session's, not the day's total. This is less severe than sleep (a workout is a discrete event, not a fragmented one) but still non-deterministic and lossy.

**Fix:** Define the multi-workout-per-day rule (e.g. sum minutes, keep the longest session's type, or pick the highest-intensity). Make the selection deterministic (sort rows before flattening). Document the chosen rule and test it.

### WR-05: `requestAuthorization` runs once per domain, presenting up to four sequential system sheets

**File:** `Sources/EndoApp/Features/Health/HealthRationaleView.swift:94-100`, `Sources/EndoApp/Health/SystemHealthDataReading.swift:52-57`

**Issue:** `proceed()` loops over all four domains calling `requestAuthorization(for:)` then `importNew(for:)`. Each `requestAuthorization` invokes `HKHealthStore.requestAuthorization(toShare:read:)` for a single domain's read set. HealthKit coalesces an already-presented set, but requesting four times sequentially (cycle, sleep, workouts, nutrition) risks presenting the sheet in stages or re-prompting, and the per-domain calls are serialized behind `@HealthStoreActor` so the user sees them one after another. The cleaner, Apple-recommended pattern is a single `requestAuthorization` for the union of all read types, then per-domain pulls.

**Fix:** Add a single `requestAuthorization()` (no domain) that requests `HealthDomain.allCases.flatMap(\.readTypes)` as one set, call it once in `proceed()`, then loop `importNew(for:)` per domain. Keeps one system sheet, preserves read-status hiding.

## Info

### IN-01: `ateThatDay == false` can never be persisted, so the column's optionality is unused for nutrition

**File:** `Sources/EndoApp/Health/SystemHealthDataReading.swift:156-164`, `Sources/EndoApp/Health/HealthSampleMapping.swift:106-108`

**Issue:** A nutrition sample is only emitted when a dietary-energy sample exists (`ateThatDay(dietaryEnergySampleCount: 1)` is hardcoded to `1`, always `true`). `ateThatDay == false` is never written, so `row.ateThatDay` is only ever `true` or `nil`. The `Bool?` column and the `count > 0` mapping helper are effectively presence-only already — fine and honest, but the `count` parameter and the `false` branch are dead in this path. Not a bug; flagged so a future reader does not assume `false` rows exist.

**Fix:** None required. Optionally document that `false` is unreachable via the reader, or drop the unused `count` parameter in favor of a presence flag.

### IN-02: `HealthDomain.sampleType` force-casts are safe but duplicate `readTypes`

**File:** `Sources/EndoApp/Health/HealthDomain.swift:32-58`

**Issue:** `readTypes` and `sampleType` independently re-list the same `HK*` types per domain. They cannot currently drift (single sample type per domain) but a future multi-type domain would require keeping both in sync by hand.

**Fix:** Optionally derive `sampleType` from the single element of `readTypes` (with a documented precondition), or leave as-is given the per-domain comments.

### IN-03: `discardUnreadableStore` resolves the store URL from `SchemaV3`, not the current `SchemaV6`

**File:** `Sources/EndoApp/App/AppDependencies.swift:274`

**Issue:** The recovery helper builds `Schema(versionedSchema: SchemaV3.self)` to discover the on-disk store URL. The URL is schema-name independent (`ModelConfiguration.url` derives from the default container/config, not the schema version), so this works today, but using a stale `SchemaV3` here is misleading and a latent trap if URL derivation ever becomes schema-sensitive. This is pre-existing (Phase 4) but now ships alongside V6.

**Fix:** Use the current schema (`SchemaV6.self`) for consistency with `makeModelContainer`, or add a comment that the URL is schema-version-independent so the `V3` choice is understood as deliberate.

### IN-04: Comment claims the recompute fires "once after a successful import that actually wrote/changed rows" but it fires on every successful pull including empty

**File:** `Sources/EndoApp/Health/HealthImportService.swift:38-39,100-103`

**Issue:** The property doc (line 38) says recompute is "Called ONCE after a successful persist that actually wrote/changed rows," but `importNew` calls `recompute()` after every successful persist, including an empty pull (line 100 comment correctly notes the coordinator's fingerprint short-circuits an unchanged recompute). The two comments contradict each other; the behavior (always call, let the coordinator dedupe) is fine and intentional.

**Fix:** Align the property doc with the actual "fires after every successful pull; the coordinator's fingerprint short-circuits unchanged work" behavior.

---

_Reviewed: 2026-06-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
