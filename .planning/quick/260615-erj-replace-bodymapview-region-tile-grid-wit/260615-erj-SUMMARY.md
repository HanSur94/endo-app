---
phase: quick-260615-erj
plan: 01
subsystem: DayLog / Body Map (SYMP-02)
tags: [swiftui, bodymap, accessibility, ios]
requires:
  - BodyRegion (Domain model, unchanged)
  - SeverityControl (reused control, unchanged)
provides:
  - Accessible human-silhouette pain map (front/back) replacing the tile grid
affects:
  - DayLogEditorView call site (API unchanged — no edit needed)
tech-stack:
  added: []
  patterns:
    - "SwiftUI Shape transcribing an SVG `d` (arcs -> addEllipse, cubics -> addCurve) scaled via CGAffineTransform"
    - "GeometryReader + .aspectRatio(200.0/440.0, .fit) + normalized UnitPoint hotspot placement"
    - "≥44pt effective hit target from a 44pt frame + .contentShape(Circle()) over a ~22pt visible dot"
key-files:
  created: []
  modified:
    - Sources/EndoApp/Features/DayLog/BodyMapView.swift
decisions:
  - "Silhouette authored once as a static `bodyPath` (Path), shared by front and back; only the hotspot set differs."
  - "One a11y element per BodyRegion via an exhaustive `hotspotCenter` switch — the compiler proves every region is placed."
  - "UI-test failure recorded as environmental (SIGKILL across all tests incl. non-bodymap) — build gate + identifier grep + the captured bodymap center-tap activity log are the binding proof."
metrics:
  duration: ~17 min
  completed: 2026-06-15
---

# Quick 260615-erj: Replace BodyMapView Region-Tile Grid with an Accessible Silhouette — Summary

Rewrote `BodyMapView`'s internals to render the approved Pencil "Body Map Pain Selection" human
silhouette (a single SwiftUI `Path` shared by front/back) with one anatomically-placed, accessible
hotspot per `BodyRegion`, replacing the old `LazyVGrid` tile grid — every behavioral and
accessibility contract (the `@Binding severities` API, the Front/Back Picker, the severity sheet,
the list fallback, and all `bodymap.*` identifiers) preserved verbatim.

## What Was Built

**Task 1 — Silhouette pain map (commit `b535f6c`)**
- Replaced the `LazyVGrid` of region tiles with a `GeometryReader` + `ZStack` sized by
  `.aspectRatio(200.0/440.0, contentMode: .fit)` so the body fits the available width, preserves the
  200:440 aspect ratio, and is never clipped.
- Added `static let bodyPath: Path` in a 200×440 view box — the head as an ellipse plus the
  torso/arms/legs as a faithful cubic-for-cubic transcription of the design's SVG `d` (encapsulated
  in a `BodySilhouette: Shape` that scales the authoring path into the supplied rect via
  `CGAffineTransform`). Front and back share the same path.
- Added `static func hotspotCenter(for:) -> UnitPoint` with an EXHAUSTIVE switch over all ten regions
  (compiler-proven coverage), normalized 0…1 to the view box, with element centers at the mandated
  anatomical points (e.g. `lowerAbdomenPelvis` at (0.500, 0.398) so the center-tapping XCUITest hits
  the right spot).
- `hotspot(_:in:)`: a `Button` whose tap sets `editingRegion = region`; marked regions show a tinted
  ~22pt `Circle` + a CENTERED numeric badge (`caption2.bold().monospacedDigit()`, accent color) so
  severity is never color-only; unmarked regions show a faint outlined dot. Wrapped in a 44pt frame +
  `.contentShape(Circle())` for a ≥44pt effective hit area, positioned via `.position(...)`. Carries
  `.accessibilityAddTraits(.isButton)`, `bodymap.region.<raw>`, the `plainLabel`, and the reused
  `accessibilityValue(for:)` ("severity N of 10" / "not marked") VERBATIM.
- The Picker (`bodymap.sideToggle`), the severity sheet (`SeverityControl` + Clear `…clear` + Done
  `…done`), the "All areas (list)" DisclosureGroup (`bodymap.listFallback`, rows `bodymap.list.<raw>`),
  the `severitySheetBinding`/`valueText`/`accessibilityValue`/`tint(for:)` helpers, and
  `extension BodyRegion: Identifiable` are byte-for-byte unchanged.
- Gate: `OK: silhouette present, tile grid removed` (all required tokens present, `LazyVGrid` gone).

**Task 2 — Regenerate, build, verify identifiers, run the bodymap UI test**
- `xcodegen generate` regenerated `EndoApp.xcodeproj` clean.
- `xcodebuild ... build CODE_SIGNING_ALLOWED=NO` → **BUILD SUCCEEDED** (Swift 6 / iOS 18, no new
  dependencies) on the booted iPhone 16 (UDID 57CC39A8-309D-44F1-86BB-5494537B49A3, iOS 26.5).
- All six identifier checks printed OK: `bodymap.region.`, `.done"`, `.clear"`, `bodymap.list.`,
  `bodymap.sideToggle`, `bodymap.listFallback`.
- Ran `xcodebuild test -only-testing:EndoAppUITests/LoggingFlowUITests` (see UI-test note below).

## Verification

- Task 1 gate: PASS (`OK: silhouette present, tile grid removed`).
- Task 2 build gate: PASS (`** BUILD SUCCEEDED **`).
- Task 2 identifier grep: PASS (all six families present).
- Only `Sources/EndoApp/Features/DayLog/BodyMapView.swift` changed; `BodyRegion.swift`,
  `SeverityControl.swift`, `project.yml`, PainPoint, the Repository, and the call site are UNTOUCHED
  (verified via `git diff --quiet`).

## UI-Test Note (environmental — recorded per the constraint)

`xcodebuild test` reported all four `LoggingFlowUITests` as failing, but **every** failure text is
`"Test crashed with signal kill."` (SIGKILL) — including `testSuppressedModeShowsNoCycleSummary()`,
which never touches the body map. The xcresult activity log proves the at-risk bodymap center-tap
contract executed and PASSED before the crash:

1. `bodymap.region.lowerAbdomenPelvis` was found, then **tapped** (the center tap landed on the
   silhouette hotspot);
2. the severity sheet opened — `bodymap.region.lowerAbdomenPelvis.done` was found, then **tapped**
   (the sheet + `.done` id resolved).

The SIGKILL occurred AFTER that, on a `swipeUp` while searching for the unrelated
`daylog.bleeding.medium` control. An isolated re-run of just `testLoggingFlow` failed even earlier
with `"Simulator device failed to launch com.endoapp.EndoAppUITests.xctrunner."` — a pure runner-launch
environment failure, not a code-level assertion. There is **no `bodymap.*` assertion failure** in any
failure text.

Per the plan's constraint (a genuine `bodymap.*` assertion is a real regression; a purely environmental
failure may be recorded as such), this is environmental: the runner was killed/failed to launch, not a
body-map regression. The **build gate + the six-identifier grep + the captured bodymap center-tap →
sheet → `.done` activity log** stand as the binding proof. No source fix is warranted (a SIGKILL /
runner-launch failure cannot be addressed in `BodyMapView.swift`). The simulator used was the booted
iPhone 16 as instructed — no substitution.

## Deviations from Plan

None — plan executed exactly as written. No auto-fixes were needed (the build was green on the first
pass and the bodymap contract passed in the UI test before the environmental crash).

## Self-Check: PASSED
- Modified file exists: Sources/EndoApp/Features/DayLog/BodyMapView.swift — FOUND
- Task 1 commit b535f6c — FOUND in git log
