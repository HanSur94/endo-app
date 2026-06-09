---
phase: 08-insights-integration-coordinator-cached-insights-dashboard
reviewed: 2026-06-09T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - Sources/EndoApp/Features/Insights/InsightsCoordinator.swift
  - Sources/EndoApp/Features/Insights/InsightFingerprint.swift
  - Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift
  - Sources/EndoApp/Features/Insights/InsightTrendChart.swift
  - Sources/EndoApp/Features/Insights/InsightCardView.swift
  - Sources/EndoApp/Features/Insights/InsightsView.swift
  - Sources/EndoApp/Features/Insights/InsightsCopy.swift
  - Sources/EndoApp/Persistence/Models/CachedInsight.swift
  - Sources/EndoApp/Persistence/Repository.swift
  - EndoInsights/Sources/EndoInsights/Insight.swift
findings:
  critical: 0
  warning: 4
  info: 4
  total: 8
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-06-09
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

Reviewed the insights integration brain (`InsightsCoordinator`), the launch-stable
fingerprint, the honest-gap chart helper + chart view, the dashboard, the cached-insight
`@Model` + Repository CRUD, and the package `Insight` value/`Codable` contract.

Against the seven adversarial focus areas the implementation is largely sound:

1. **Off-main concurrency** — only the pure `engine(...)` runs in `Task.detached`; `loadSnapshots()`/`loadDismissed()` are read on the `@MainActor` before the hop; `Sendable` value types cross the boundary; results are published back on the main actor. No SwiftData read happens off-main. **However the `isUpdating` flag and `lastFingerprint` stamping are not cancellation-safe across overlapping recomputes** (WR-01) and `isUpdating` can be left stuck `true` (WR-02).
2. **Dismiss cross-context correctness** — the prior reappear bug is genuinely fixed: `AppDependencies` wires `persistDismissal`, `loadDismissed`, and `loadSnapshots` through the SAME `insightsRepo`/`ModelContext`, so a dismissal is visible to the very next recompute and the gate excludes it by `signalKey`. No path found where a dismissed insight reappears. The stale doc-comment in `InsightsView` (IN-01) describes the OLD separate-context path but the code is correct.
3. **CachedInsight serialization/CRUD** — CloudKit-safe (all optional/defaulted, no `.unique`, app-gen UUID, no relationships); consent-gated; corrupt-blob tolerant via `compactMap { try? decode }`. `Insight.id` round-trips because it is `Codable` and deterministically derived. One robustness gap: a dismissed insight that is still present in a stale cache row can flash on cold open before the first recompute (WR-03).
4. **Chart gap logic** — `ChartSeriesGrouping` never fabricates `value:0`; distinct series per contiguous run; a real logged 0 is kept. Correct.
5. **Honesty surfacing** — the dashboard renders only the coordinator's already-gated insights; no causal copy, no numeric confidence, suppressed → no cycle-phase section. Correct.
6. **Fingerprint correctness** — edit-sensitive (latest-day epoch flips on a same-count edit) and launch-stable (string-built, no `Set.hashValue`). One real correctness gap: a non-latest-day edit does NOT change the fingerprint (WR-04).
7. **Crashes** — no force-unwraps on untrusted data, JSON decode is `try?`-guarded, `Dictionary(uniqueKeysWithValues:)` is not used. One latent `ForEach` duplicate-id concern (IN-02).

No Critical findings. Four Warnings and four Info items below.

## Warnings

### WR-01: Overlapping recomputes can stamp a stale fingerprint and publish stale insights

**File:** `Sources/EndoApp/Features/Insights/InsightsCoordinator.swift:126-150`
**Issue:** `recompute()` reads inputs and computes `fingerprint` on the main actor, then
`await`s the detached engine. The `await` is a suspension point — during it, the actor is
free to run another `recompute()` (e.g. a second `dataChanged()` whose debounce already
fired, or the `onAppear` `dataChanged()` racing a foreground `dataChanged()`). Because
`recompute()` is not cancelled when the debounce task is cancelled (the debounce only
cancels the `Task.sleep`, not an already-started `recompute`), two recomputes can interleave:

```
A: reads snapshots(t0), fingerprint=F0, isUpdating=true, await engine ──────┐
B: reads snapshots(t1), fingerprint=F1, isUpdating=true, await engine ───┐  │
B: resumes → lastFingerprint=F1, insights=fresh1, isUpdating=false       │  │
A: resumes → lastFingerprint=F0, insights=fresh0 (STALE), isUpdating=false ──┘
```

The last writer wins, but ordering of resumption is not guaranteed to match input recency.
The result is the published `insights` and `lastFingerprint` can be set from the OLDER
input set, so a subsequent identical recompute then short-circuits on the stale fingerprint
and the dashboard shows out-of-date insights until the next genuine input change.

**Fix:** Make recompute single-flight. Track the in-flight task and re-entrancy guard, e.g.:
```swift
private var recomputeTask: Task<Void, Never>?

func recompute() async {
    recomputeTask?.cancel()
    let task = Task { @MainActor in
        guard let snapshots = try? loadSnapshots(),
              let dismissed = try? loadDismissed() else { return }
        let fingerprint = InsightFingerprint.make(snapshots: snapshots, dismissed: dismissed)
        guard fingerprint != lastFingerprint else { return }
        isUpdating = true
        defer { isUpdating = false }
        let asOf = now()
        let engine = self.engine
        let fresh = await Task.detached(priority: .userInitiated) {
            engine(snapshots, asOf, dismissed)
        }.value
        guard !Task.isCancelled else { return }   // a newer recompute superseded us
        lastFingerprint = fingerprint
        insights = fresh
        persist(fresh, asOf, fingerprint)
    }
    recomputeTask = task
    await task.value
}
```
At minimum, re-check `lastFingerprint` (or a monotonically increasing generation counter)
AFTER the `await` and bail if a newer recompute already published.

### WR-02: `isUpdating` is left stuck `true` if the input read throws or the engine task is cancelled

**File:** `Sources/EndoApp/Features/Insights/InsightsCoordinator.swift:128-149`
**Issue:** `isUpdating = true` is set on line 135, but `isUpdating = false` only runs on the
single happy-path exit (line 149). The early `guard` on lines 128-129 (a `loadSnapshots()`
/`loadDismissed()` throw) returns BEFORE `isUpdating` is set, which is fine. But once
`isUpdating = true` has been set, there is no `defer`/`catch` guaranteeing it is reset. If a
future change adds a `throw`/early-return between lines 135 and 149, or the detached task is
cancelled (its `.value` still returns, but a refactor to a throwing/cancellable engine would
not), the dashboard's "Updating…" caption (`insights.updating`) stays on screen forever.
This is a latent stuck-spinner defect; the current single happy path masks it only by luck.
**Fix:** Wrap the post-flag body in `defer { isUpdating = false }` immediately after setting
it `true`:
```swift
isUpdating = true
defer { isUpdating = false }
```

### WR-03: A stale/dismissed cached insight can flash on cold open before the first recompute excludes it

**File:** `Sources/EndoApp/Features/Insights/InsightsCoordinator.swift:97-99, 117-122`; `Sources/EndoApp/Features/Insights/InsightsView.swift:90-94`
**Issue:** `loadCached()` publishes whatever is in `CachedInsight` rows verbatim. The cache is
the LAST recompute's output. If a user dismisses an insight, the optimistic filter (line 120)
removes it from the in-memory `insights`, and the following recompute persists a fresh cache
WITHOUT it — so far correct. But the dismissal is only excluded from the CACHE after that
recompute's `persist(...)` completes. If the app is killed after `persistDismissal` succeeds
but BEFORE the recompute's `replaceCachedInsights` runs (the recompute is debounced ~400ms
and runs off-main), the next cold open's `loadCached()` will publish the still-present
dismissed insight for up to ~400ms+engine-time until the recompute re-reads the dismissed set
and drops it. The card visibly reappears then disappears. This is the exact "stays gone"
guarantee the phase calls out, weakened at the cache layer. Not Critical because the dismissal
IS durable (it persisted) and the recompute does eventually exclude it, but the flash is a
user-visible regression of the stays-gone promise.
**Fix:** Either (a) filter `loadCached()` output through the current `dismissedSignalKeys()`
on the main actor before publishing, or (b) on `dismiss(signalKey:)`, also persist the updated
cache synchronously (replace-all with the optimistically-filtered list) so the cache never
contains a dismissed signal even across an immediate kill.

### WR-04: Fingerprint misses an edit to a non-latest logged day (same count, older day changed)

**File:** `Sources/EndoApp/Features/Insights/InsightFingerprint.swift:41-49`
**Issue:** The fingerprint is `count | latestDayEpoch | dismissedCanonical`. The doc claims it
flips when "editing the most recent day's severity" — true. But editing the severity of an
OLDER already-logged day (not the max `day`, not adding/removing a day) changes neither the
count nor the max day, so the fingerprint is IDENTICAL and the recompute SHORT-CIRCUITS on the
cache hit (line 133 of the coordinator). The user edits a symptom severity from three days ago,
the trend chart should move, but the insights are not recomputed until some other input
changes. Because `DaySnapshot` mapping uses `startOfDay` and the fingerprint only samples
count + max-day, in-place edits to interior days are invisible. The comment frames `latestDay`
as covering "an edit to the most recent logged day" — it does, but the engine recomputes trends
over the WHOLE window, so any interior edit must also invalidate.
**Fix:** Fold a value-derived summary of ALL days into the fingerprint, not just count + max.
A cheap launch-stable option: sort days and join a per-day digest (e.g. day epoch + a stable
hash of that day's severities), or include a sum/canonical join of `(dayEpoch:severitySignature)`
pairs. Keep it string-built (never `Set.hashValue`) to preserve launch stability:
```swift
let perDay = snapshots
    .sorted { $0.day < $1.day }
    .map { "\($0.day.timeIntervalSince1970):\($0.symptoms.map { "\($0.type.rawValue)=\($0.severity ?? -1)" }.sorted().joined(separator:";"))" }
    .joined(separator: "|")
```
(or whatever fields the trend/correlation engines actually consume), so any in-place edit flips it.

## Info

### IN-01: Stale doc-comment in `InsightsView` describes the OLD (buggy) separate-context dismiss path

**File:** `Sources/EndoApp/Features/Insights/InsightsView.swift:27-29, 151-155`
**Issue:** The header comment says each card's `onDismiss` "builds a
`SwiftDataRepository(context: modelContext)` (mirror `HomeView`'s seam), calls
`dismissInsight(signalKey:)` … then `coordinator.dataChanged()`". That is precisely the
cross-context bug the phase fixed — the actual `dismiss(_:)` (line 156-158) correctly routes
through `coordinator.dismiss(signalKey:)`, which persists through the coordinator's OWN
context. The stale comment will mislead the next maintainer into reintroducing the bug.
**Fix:** Update the lines 27-29 comment to describe the coordinator-routed dismissal (as the
line 151-155 comment already correctly does).

### IN-02: Chart `ForEach(..., id: \.date)` assumes one point per day; duplicate dates would silently drop marks

**File:** `Sources/EndoApp/Features/Insights/InsightTrendChart.swift:85, 98`
**Issue:** Both the per-run `LineMark` loop and the `PointMark` loop key `ForEach` on
`\.date`. If `insight.points` ever contains two points with the same `Date` (e.g. a producer
emits two same-day points, or a future non-start-of-day timestamp), SwiftUI's `ForEach` with
duplicate ids renders only one and logs a runtime warning — silently dropping data. The
grouping helper's `areAdjacentDays` comment even notes "a repeated same-day point would not
extend the run", acknowledging same-day points are conceivable. Not a crash and not currently
reachable (producers emit one start-of-day point per day), but fragile.
**Fix:** Either assert/dedupe points by start-of-day before rendering, or use a stable
`Identifiable` index-based id instead of `\.date`.

### IN-03: `directionWord` recomputes a full sort on every render and duplicates engine direction logic

**File:** `Sources/EndoApp/Features/Insights/InsightCardView.swift:144-152`
**Issue:** The card derives "trending up/down/steady" from `points.first`→`points.last` after
sorting, independently of whatever direction the engine used to write the `headline`. If the
engine's notion of direction (e.g. a regression slope over the window) ever disagrees with a
naive first-vs-last comparison on sparse points, the chart summary text and the headline can
contradict each other (headline says one thing, summary says "trending down"). The view should
not re-derive a semantic the engine owns.
**Fix:** Have the engine surface the direction word on the `Insight` (or in `points` metadata)
and render it verbatim, rather than re-deriving it in the view. At minimum, document that
first-vs-last is an intentional, separate display heuristic.

### IN-04: `confidenceWord` maps `.none` to the "Low" wording, which can mislabel a withheld-but-rendered insight

**File:** `Sources/EndoApp/Features/Insights/InsightsCopy.swift:32-39`; `InsightCardView.swift:88-93`
**Issue:** `confidenceWord(.none)` returns `confidenceLow` ("Low — possible association"), and
`confidenceSymbol` collapses `.low, .none` to the empty `circle`. The comment asserts "the
gate never surfaces a `.none`-confidence card". That invariant is enforced in
`HonestyGate` (confidence floor `.low`), so today this branch is unreachable for non-insufficiency
cards. But if a future producer or a regression lets a `.none`-confidence card through, the UI
would silently present it as "Low" confidence — overstating confidence in a privacy/honesty-
critical surface. The total mapping is defensive, but the chosen fallback rounds UP.
**Fix:** Treat `.none` as a hard "do not render a confidence claim" (omit the pill) rather than
labeling it "Low", so an escaped `.none` card cannot overstate confidence.

---

_Reviewed: 2026-06-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
