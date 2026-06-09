# Phase 9: HealthKit Import - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning
**Source:** Smart discuss (autonomous mode) — 4 grey areas; user accepted all recommendations.

<domain>
## Phase Boundary

Add **Apple HealthKit as a purely additive, read-only source**: import cycle (menstrual flow), sleep, workouts, and nutrition into a new `ImportedSample` store, funneled through the **same `DaySnapshot` → insights pipeline**, with **contextual, incremental permission prompts** (in-app rationale before the system sheet) and **full usability when access is denied or empty**.

In scope: DATA-05. The `ImportedSample` model + a HealthKit read service + the Repository mapping that merges imported data into `daySnapshots()` (manual-wins) + contextual permission UX + graceful denial.

Out of scope: HealthKit **write-back / export** (PLAT-01, deferred); background delivery (`HKObserverQuery`); basal body temp / cervical mucus; full micronutrient import. Insight engine logic itself is unchanged — Phase 9 only adds a new *source* feeding the existing contract.
</domain>

<decisions>
## Implementation Decisions (smart discuss — all recommendations accepted)

### Area 1 — Manual ↔ imported reconciliation & provenance
- **D-01:** Imported samples persist in a **separate, additive, CloudKit-safe `ImportedSample` @Model** (new SchemaV(N+1), lightweight migration) — it NEVER mutates or overwrites manual-logging rows.
- **D-02:** **Manual logging always wins.** Imported data fills only days/fields where the user has NO manual entry. A manual entry is never displaced by a Health sample.
- **D-03:** **Provenance is visible** — any value sourced from Apple Health is labeled "from Apple Health" in the UI (badge/source tag) so the user always knows where a value came from.
- **D-04:** In the `DaySnapshot` mapping, a Health-sourced value is used ONLY where the manual value is absent, and it **respects `nil` = unlogged** (Phase 5 D-03 — never coerce absence to 0). Once mapped, the engine treats a Health-sourced value identically to a manual one. A denied/empty Health read contributes nothing — it must never read as "no symptoms" (SC3).

### Area 2 — Permission sequencing & rationale (SC2)
- **D-05:** **Contextual, per-domain permission** — request a HealthKit category's read access at the moment the user opts into that data (e.g. taps "Connect sleep from Health" on the sleep surface), NOT one mega-prompt at launch.
- **D-06:** Each request shows an **in-app rationale screen BEFORE Apple's system sheet**: plain-language statement of what's read, that it's **read-only**, stays on-device / in private iCloud, and is **optional** — then "Continue" triggers the `HKHealthStore` authorization request.
- **D-07:** **Entry points:** a "Connect Apple Health" section in **Settings** PLUS contextual connect affordances on the relevant logging surfaces.
- **D-08:** **On denial: never nag.** Show a passive "Connect Apple Health" affordance the user can tap later; if previously denied, deep-link to the Settings app (HealthKit auth can't be re-requested in-app once denied). The app stays **fully usable via manual logging** (SC3).

### Area 3 — Refresh model
- **D-09:** **Foreground incremental pull** via `HKAnchoredObjectQuery` per granted type — fetch only new/changed samples since the last persisted anchor (efficient, no full re-scan).
- **D-10:** Also provide a **manual "Refresh from Health"** action (Settings button / pull-to-refresh) for explicit user control.
- **D-11:** **Defer background delivery** (`HKObserverQuery` + `enableBackgroundDelivery`) — foreground + anchored pull meets SC1 without the extra background-mode entitlement complexity. Noted as a future enhancement.
- **D-12:** After an import writes new `ImportedSample`s, call the **Phase-8 `InsightsCoordinator.dataChanged()`** so insights recompute off-main (reuse the existing seam; no new recompute machinery).

### Area 4 — Backfill window & type scope
- **D-13:** **First-grant backfill = last 365 days** (covers all-time / 90-day trend windows + recent cycle history) via a bounded anchored query; subsequent syncs are incremental from the stored anchor.
- **D-14:** **Cycle = menstrual flow** (`HKCategoryType(.menstrualFlow)`) only; defer basal body temp / cervical mucus (not consumed by current `CycleMath`).
- **D-15:** **Sleep** = `HKCategoryType(.sleepAnalysis)` → nightly asleep **hours + a coarse quality** mapped onto the app's `SleepQuality`. **Workouts** = `HKWorkout` → app `ActivityType` + minutes (map common `HKWorkoutActivityType`; "other" fallback).
- **D-16:** **Nutrition** = **meal-event presence** ("ate that day", from dietary energy / meal samples) matching the app's **tag-based `FoodTag` model** — NOT a full micronutrient breakdown.

### Claude's Discretion
- The exact `ImportedSample` schema shape, the `HKWorkoutActivityType → ActivityType` mapping table, the sleep-stage → `SleepQuality` thresholds, the rationale-screen copy (must pass `ClaimsPolicy`), the anchor-persistence mechanism, and the precise Settings/contextual UI layout are left to research/planning.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`Repository.daySnapshots()`** (Sources/EndoApp/Persistence/Repository.swift) — the `[DaySnapshot]` mapping the engine consumes; imported data merges in HERE (manual-wins). Sibling reads: `factorDays`, `bleedingDays`.
- **Domain value types** (Sources/EndoApp/Domain/): `FactorDay`, `SleepQuality`, `ActivityType`, `ActivityIntensity`, `FoodTag`, `FlowIntensity`, `CycleMath`, `CycleSummary` — imported data maps onto these same types.
- **`InsightsCoordinator.dataChanged()`** (Phase 8, Sources/EndoApp/Features/Insights/InsightsCoordinator.swift) — the recompute trigger to call after import.
- **Consent gate** (Phase 1/2 AppGate `.consented` branch) — health UI only after app consent; HealthKit auth is a SEPARATE, additional Apple-level grant on top.
- **Additive-schema pattern** (Phase 6/8): new @Model under `extension SchemaV1` → SchemaV(N+1) → MigrationPlan lightweight stage → ModelContainer bump → +1 survival test. `ImportedSample` follows this exactly.

### Established Patterns
- Privacy-first, additive, append-only schema; CloudKit-safe @Models (no `.unique`, app-generated UUIDs); `nil` = unlogged never 0; consent-gated Repository CRUD; `ClaimsPolicy` choke-point for all user-facing copy.
- Entitlements already ship CloudKit; **HealthKit entitlement + `NSHealthShareUsageDescription` Info.plist string are NOT yet present** — Phase 9 adds them (read-only; no `NSHealthUpdateUsageDescription` since write-back is out of scope).

### Integration Points
- New HealthKit read service (app-side, outside the Foundation-only `EndoInsights` package — HealthKit must NOT enter the package, mirroring the SwiftData boundary).
- `ImportedSample` persistence in the SwiftData store; merge at `daySnapshots()`.
- Settings + contextual surfaces for the connect/permission UX.
</code_context>

<specifics>
## Specific Ideas

- Imported values must be visibly labeled "from Apple Health" (D-03).
- Rationale screen must state read-only + private + optional before the system sheet (D-06).
- SC3 is the honesty anchor: denied or empty Health reads must NEVER render as "you have no symptoms" — enforced by `nil`=unlogged (D-04) and absence-by-omission (Phase 8 pattern).
</specifics>

<deferred>
## Deferred Ideas

- HealthKit **write-back / export** (export logged data to Apple Health) — PLAT-01, explicitly out of scope for v1.
- **Background delivery** (`HKObserverQuery` + `enableBackgroundDelivery`) — future enhancement; foreground anchored pull suffices now.
- **Basal body temp / cervical mucus** cycle signals — not consumed by current cycle logic.
- **Full micronutrient** nutrition import — current food model is tag-based, not nutrient-quantified.
</deferred>

<canonical_refs>
## Canonical References

### Project & Requirements
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md` — Phase 9 req: DATA-05 (HealthKit read-only import; app usable if denied)
- `.planning/ROADMAP.md` — Phase 9 goal + 3 success criteria

### Prior phase decisions carried forward
- `.planning/phases/05-snapshot-contract-descriptive-trends-engine/05-CONTEXT.md` — `DaySnapshot` contract; D-03 `nil`=unlogged never 0 (the SC3 honesty mechanism)
- `.planning/phases/04-private-cloudkit-sync/04-CONTEXT.md` — entitlement/permission posture, runtime-gated, consent-first
- `.planning/phases/08-insights-integration-coordinator-cached-insights-dashboard/08-CONTEXT.md` — `InsightsCoordinator.dataChanged()` recompute seam; additive-schema A1 checklist

### Key implementation files
- `Sources/EndoApp/Persistence/Repository.swift` — `daySnapshots()` merge point
- `Sources/EndoApp/Domain/` — `FactorDay`, `SleepQuality`, `ActivityType`, `FoodTag`, `FlowIntensity`, `CycleMath`
- `Sources/EndoApp/Resources/Info.plist` + `EndoApp.entitlements` — add HealthKit read entitlement + `NSHealthShareUsageDescription`
</canonical_refs>
