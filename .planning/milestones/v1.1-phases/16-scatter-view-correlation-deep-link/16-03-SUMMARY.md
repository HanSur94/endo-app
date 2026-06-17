---
phase: 16-scatter-view-correlation-deep-link
plan: 03
subsystem: ui
tags: [swift, swiftui, deep-link, observable, environment-router, tabview, cross-tab, ios]

# Dependency graph
requires:
  - phase: 16-scatter-view-correlation-deep-link/01
    provides: ScatterDeepLink.resolve(signalKey:suppressed:) — the resolver gating the affordance + computing the typed (x,y) axes; ExploreCopy.scatterLinkLabel
  - phase: 16-scatter-view-correlation-deep-link/02
    provides: ExploreView ephemeral mode/scatterX/scatterY @State + ScatterPlotView/ScatterAxisPicker — the scatter the deep-link prefills
  - phase: 03-reminders-deep-link
    provides: CheckInRouter — the @MainActor @Observable env-router shape copied verbatim (EnvironmentKey + MainActor.assumeIsolated default)
provides:
  - "ExploreScatterRouter: @MainActor @Observable cross-tab router carrying an Equatable PendingScatter(x,y) intent, env-injected like CheckInRouter, ephemeral (no persistence)"
  - "ConsentedShell TabView(selection:) + per-tab .tag over a ConsentedTab enum — the affordance can programmatically switch to the Explore tab"
  - ".correlation card 'See the days behind this' affordance, shown IFF ScatterDeepLink.resolve != nil; InsightsView threads it -> router payload + Explore-tab select"
  - "ExploreView router read: on appear / on change consume PendingScatter -> mode=.scatter + prefill X/Y + clear (re-trigger safe)"
affects: [16-04-card-affordance-deep-link-e2e]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Cross-tab @Observable env-router carrying a typed non-sensitive intent value (PendingScatter), observed ONLY in the consented branch (CheckInRouter/T-03-14 posture extended to a tab-switch)"
    - "Equatable wrapper struct (PendingScatter) over a pair so .onChange(of:) compiles — a tuple is not Equatable"
    - "Resolver-gated affordance: compute ScatterDeepLink.resolve ONCE, render the Button only when non-nil (HIDDEN, not disabled); carry the resolver's TYPED axes, never re-derive from audited copy"
    - "Consume-then-clear deep-link payload into ephemeral @State (re-trigger safety) — on appear AND on change"

key-files:
  created:
    - Sources/EndoApp/App/ExploreScatterRouter.swift
  modified:
    - Sources/EndoApp/App/AppGate.swift
    - Sources/EndoApp/App/EndoAppApp.swift
    - Sources/EndoApp/App/AppDependencies.swift
    - Sources/EndoApp/Features/Insights/InsightCardView.swift
    - Sources/EndoApp/Features/Insights/InsightsView.swift
    - Sources/EndoApp/Features/Explore/ExploreView.swift

key-decisions:
  - "ConsentedTab is a typed enum (not Int) so the affordance selects .explore by symbol; the selectedTab binding is threaded from ConsentedShell -> InsightsView (the affordance closure sets the router payload then selects .explore)"
  - "PendingScatter is an Equatable struct wrapping the two ExploreSeries (not a tuple) so .onChange(of: router.pendingScatter) compiles — ExploreSeries is Equatable by id"
  - "scatterAxes computed ONCE as a private var (insight.kind == .correlation ? ScatterDeepLink.resolve(...) : nil); the affordance renders only when non-nil so presence/sleep-quality/suppressed-bleeding cards show NO link"
  - "InsightsView reads suppressed ONCE per section via a per-view SwiftDataRepository (mirrors ExploreView.suppressed); a read failure degrades to false, which can only KEEP the bleeding gate, never expose it"
  - "ExploreView consumes the payload on BOTH .onAppear (deep-link landed before observing) and .onChange(of: pendingScatter), then clears it"

requirements-completed: []  # SCAT-04 left Pending — the full deep-link E2E proof lands in 16-04 per the execution context

# Metrics
duration: 12min
completed: 2026-06-16
---

# Phase 16 Plan 03: Cross-Tab Scatter Deep-Link Wiring (SCAT-04) Summary

**An `@Observable ExploreScatterRouter` carrying an `Equatable PendingScatter(x,y)` intent (CheckInRouter shape), a `ConsentedShell TabView(selection:)` + `.tag`, the `.correlation` card's resolver-gated "See the days behind this" affordance, the `InsightsView` threading (router payload + Explore-tab select), and the `ExploreView` router read that prefills the scatter and switches to it — the full cross-tab seam for SCAT-04, minus the E2E proof (16-04).**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-06-15T23:33:37Z
- **Completed:** 2026-06-16 (this session)
- **Tasks:** 3
- **Files modified:** 7 (1 created, 6 modified)

## Accomplishments
- **Router seam (`ExploreScatterRouter.swift`):** a `@MainActor @Observable final class` carrying `var pendingScatter: PendingScatter?`, a `struct PendingScatter: Equatable { let x, y: ExploreSeries }` (a wrapper so `.onChange(of:)` compiles — a tuple is not Equatable), a private `ExploreScatterRouterKey: EnvironmentKey` with a `MainActor.assumeIsolated { ExploreScatterRouter() }` default, and the `EnvironmentValues.exploreScatterRouter` accessor — copied verbatim from `CheckInRouter`'s shape. Carries only catalog series ids (public, non-sensitive); EPHEMERAL (no persistence wrapper, grep-gated); the T-03-14/T-16-11/T-16-12 security-invariant comment posture carried.
- **Shell edit (`AppGate.swift`):** the bare `TabView` becomes `TabView(selection: $selectedTab)` over a new `ConsentedTab { log, insights, explore }` enum with a per-tab `.tag`; the id-on-`Label` `tabItem`s are unchanged (`tab.insights`/`tab.explore` preserved). `selectedTab` is ephemeral `@State` threaded into `InsightsView(selectedTab:)`.
- **Injection (`EndoAppApp.swift` / `AppDependencies.swift`):** `exploreScatterRouter` declared + constructed alongside `checkInRouter`, injected at the `WindowGroup` via `.environment(\.exploreScatterRouter, …)`.
- **Affordance (`InsightCardView.swift`):** added `var suppressed: Bool = false` + `var onOpenScatter: (PendingScatter) -> Void = { _ in }` (the `onDismiss` screen-supplied-action precedent), a `scatterAxes` computed ONCE via `ScatterDeepLink.resolve` (nil for non-correlation / presence / sleep-quality / suppressed-bleeding cards), and — `if let axes = scatterAxes` in the `.correlation` arm — a `Button` with `Label(ExploreCopy.scatterLinkLabel, systemImage: "arrow.up.forward.square")`, `.buttonStyle(.borderless)`, accent tint, ≥44pt hit area, id `\(idRoot).scatterLink`. The affordance carries the resolver's TYPED axes — it NEVER reads `insight.headline`/`detail` (T-16-14, grep-gated).
- **Threading (`InsightsView.swift`):** reads `suppressed` ONCE per section via a per-view `SwiftDataRepository(context:)` (mirroring `ExploreView.suppressed`), observes `\.exploreScatterRouter`, takes the `selectedTab` binding, and threads `suppressed` + an `onOpenScatter` closure (sets `scatterRouter.pendingScatter` THEN selects `.explore`) into each card.
- **Router read (`ExploreView.swift`):** added `@Environment(\.exploreScatterRouter)` + a `consumePendingScatter()` helper called on `.onAppear` (deep-link landed before observing) AND `.onChange(of: scatterRouter.pendingScatter)` — sets `mode = .scatter`, prefills `scatterX`/`scatterY`, then CLEARS `pendingScatter` (re-trigger safe). The payload lands ONLY in the existing ephemeral `@State`.
- **Proven:** clean Swift 6 build after `xcodegen generate`; `InsightsDashboardUITests` 6/6 + `ExploreUITests` 8/8 green; every honesty + privacy grep gate clean (router has no persistence wrapper; affordance reads the resolver's typed value, never headline/detail; `TabView(selection:)` + per-tab `.tag` present; ExploreView consumed payload lands in ephemeral state).

## Task Commits

1. **Task 1: ExploreScatterRouter + PendingScatter + ConsentedShell TabView(selection:)** — `48d1a6f` (feat)
2. **Task 2: .correlation card scatter-link affordance + InsightsView threading** — `c6e7897` (feat)
3. **Task 3: ExploreView router read — prefill + switch to scatter, then clear** — `25b8800` (feat)

_All three tasks are `tdd="true"`; their `<verify>` blocks are build + the honesty/privacy grep gates + the existing UI regression suite (the gate IS the verification for these SwiftUI Views / wiring — no separate unit-test method, mirroring 16-01 Task 1 and all of 16-02). The full deep-link behavioral E2E lands in 16-04 per the execution context._

## Files Created/Modified
- `Sources/EndoApp/App/ExploreScatterRouter.swift` — the `@Observable` cross-tab router + `Equatable PendingScatter` value type + `EnvironmentKey`, CheckInRouter shape, ephemeral.
- `Sources/EndoApp/App/AppGate.swift` — `ConsentedTab` enum + `TabView(selection: $selectedTab)` + per-tab `.tag`; threads `selectedTab` into `InsightsView`.
- `Sources/EndoApp/App/EndoAppApp.swift` — `.environment(\.exploreScatterRouter, …)` injection.
- `Sources/EndoApp/App/AppDependencies.swift` — `exploreScatterRouter` declared + constructed.
- `Sources/EndoApp/Features/Insights/InsightCardView.swift` — `suppressed` + `onOpenScatter`, `scatterAxes` resolver gate, the `.correlation` scatter-link `Button`.
- `Sources/EndoApp/Features/Insights/InsightsView.swift` — `suppressed` per-view read, router env, `selectedTab` binding, card threading (payload + tab select).
- `Sources/EndoApp/Features/Explore/ExploreView.swift` — router env + `consumePendingScatter()` on appear/on change.

## Decisions Made
- **Tab switch via a threaded binding, not a second router field:** `ConsentedShell` owns the `selectedTab` `@State` and threads a `Binding<ConsentedTab>` into `InsightsView`; the affordance closure sets the router payload then `selectedTab.wrappedValue = .explore`. This keeps the router carrying ONLY the typed scatter intent (no UI-navigation state), and the tab-switch stays ephemeral shell state.
- **Set payload BEFORE selecting the tab:** the closure sets `scatterRouter.pendingScatter` first, then selects `.explore`, so the value is present the moment `ExploreView` observes the router on appear/onChange (no ordering race).
- **Consume on BOTH appear and change:** `ExploreView` reads the router on `.onAppear` (covering a deep-link that lands the user on Explore before the view observes the change) AND `.onChange(of: pendingScatter)`, then clears — so an identical later tap (same x/y) re-fires.
- **Suppression read mirrors ExploreView:** `InsightsView.suppressed` is `(try? SwiftDataRepository(context: modelContext).daySnapshots())?.last?.suppressed ?? false` — a read failure degrades to `false`, which can only KEEP the bleeding gate (the resolver hides bleeding only when `suppressed == true`), never expose a bleeding axis it should hide.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pass `onDismiss` explicitly rather than as a trailing closure**
- **Found during:** Task 2 (InsightsView build)
- **Issue:** Adding `suppressed`/`onOpenScatter` AFTER the existing `onDismiss` parameter meant the call site's trailing closure mapped to `onDismiss` only when it was the last unfilled closure param — Swift reported `incorrect argument labels in call (have 'insight:suppressed:onOpenScatter:_:', expected 'insight:onDismiss:suppressed:onOpenScatter:')`.
- **Fix:** Changed the call site to pass `onDismiss: { dismiss(insight) }` explicitly (no trailing closure), so all four args bind by label. No behavior change; the card's parameter order is unchanged.
- **Files modified:** `Sources/EndoApp/Features/Insights/InsightsView.swift`
- **Verification:** Build succeeds; `InsightsDashboardUITests` 6/6 green.
- **Committed in:** `c6e7897` (Task 2 commit)

**2. [Rule 1 - Bug] Rephrased the router doc comment so the persistence grep gate reads clean**
- **Found during:** Task 1 (persistence gate)
- **Issue:** The plan's persistence acceptance grep (`grep -nE '@AppStorage|UserDefaults|@SceneStorage' … | grep -vc '^#'`) only excludes shell-style `#` comments, not Swift `//` comments. My honesty-documenting header comment quoted `@AppStorage`/`UserDefaults`/`@SceneStorage` verbatim to state the router never uses them — producing 2 false-positive grep matches with zero actual code occurrences (the identical false-positive 16-01 hit, deviation 2).
- **Fix:** Rephrased the comment to describe the forbidden tokens in prose ("no app-storage, user-defaults, or scene-storage property wrapper"). No code/logic changed.
- **Files modified:** `Sources/EndoApp/App/ExploreScatterRouter.swift`
- **Verification:** The gate now returns 0; build still clean.
- **Committed in:** `48d1a6f` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking compile fix, 1 cosmetic gate-hygiene). Both necessary; no behavior change, no scope creep.

## Known Stubs

None. The `var onOpenScatter: (PendingScatter) -> Void = { _ in }` and `var suppressed: Bool = false` defaults on `InsightCardView` are the established screen-supplied-action precedent (matching `onDismiss = {}`) so previews/tests compile — the live `InsightsView` always supplies both. The `.constant(.insights)` default on `InsightsView.selectedTab` is likewise a preview/test default; the live `ConsentedShell` always supplies the real binding.

## Threat Surface Scan

No new security-relevant surface beyond the plan's `<threat_model>`. The router carries only public catalog series ids (T-16-11 mitigated: ephemeral, no persistence wrapper, grep-gated); it is observed ONLY inside `AppGate`'s `.consented` branch (T-16-12: the `TabView` itself exists only there); the affordance gate honors suppression via `ScatterDeepLink.resolve(suppressed:)` (T-16-13); the affordance carries the resolver's typed value, never parsing audited copy (T-16-14, grep-confirmed). No new network/auth/file/schema surface.

## Issues Encountered
- The host simulator did NOT flake this run; both regression suites ran clean on the first build (InsightsDashboardUITests 6/6, ExploreUITests 8/8). No per-method fallback needed.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- **Ready for 16-04 (deep-link E2E):** the full cross-tab seam is in place — a `.correlation` card with a quantitative-factor association renders `insights.card.<signalKey>.scatterLink`; tapping it sets the router payload + selects the `tab.explore` tab; `ExploreView` consumes the payload into `explore.mode.scatter` + the prefilled `explore.scatter.axisX|Y`. 16-04 adds the seed + E2E that exercise this wiring end-to-end and flips the SCAT-01..04 checkboxes.
- **PDF path untouched;** the lanes/dashboard regression suites stay green; mode + X/Y + selected-tab + the router payload are all ephemeral (the LOCKED privacy invariant extended to the deep-link).
- **Requirements:** SCAT-04 left Pending (E2E proof in 16-04) per the execution context.

## Self-Check: PASSED

- FOUND: `Sources/EndoApp/App/ExploreScatterRouter.swift`
- FOUND: `.planning/phases/16-scatter-view-correlation-deep-link/16-03-SUMMARY.md`
- FOUND commit: `48d1a6f` (Task 1)
- FOUND commit: `c6e7897` (Task 2)
- FOUND commit: `25b8800` (Task 3)

---
*Phase: 16-scatter-view-correlation-deep-link*
*Completed: 2026-06-16*
