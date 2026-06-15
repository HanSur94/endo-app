---
phase: quick-260615-erj
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - Sources/EndoApp/Features/DayLog/BodyMapView.swift
autonomous: true
requirements: [SYMP-02]
tags: [swiftui, bodymap, accessibility, ios]

must_haves:
  truths:
    - "The body map renders a human silhouette (a SwiftUI Path) instead of a flow of rectangular tiles."
    - "Each BodyRegion in the front/back split is a single tappable accessibility element placed anatomically over the silhouette."
    - "Tapping a region hotspot opens the existing severity sheet (sets editingRegion), exactly as the old tiles did."
    - "A marked region shows a filled severity-tinted dot PLUS a visible numeric badge; an unmarked region shows a faint outlined dot — severity is never color-only."
    - "Every hotspot effective hit target is >= 44pt even when the visible dot is smaller, with the element center over the intended anatomical spot."
    - "The Front/Back Picker, the All-areas list fallback, and the severity sheet (Clear/Done) keep working with their existing accessibility identifiers."
    - "The @Binding severities public API and the DayLogEditorView call site are unchanged; the app/package build stays green."
  artifacts:
    - path: "Sources/EndoApp/Features/DayLog/BodyMapView.swift"
      provides: "Silhouette body-map view with anatomically-placed accessible region hotspots"
      contains: "GeometryReader"
  key_links:
    - from: "Sources/EndoApp/Features/DayLog/BodyMapView.swift"
      to: "editingRegion"
      via: "hotspot tap sets editingRegion = region"
      pattern: "editingRegion = region"
    - from: "Sources/EndoApp/Features/DayLog/BodyMapView.swift"
      to: "BodyRegion.plainLabel"
      via: "accessibilityLabel on each hotspot"
      pattern: "region.plainLabel"
    - from: "Sources/EndoApp/Features/DayLog/BodyMapView.swift"
      to: "bodymap.region identifier family"
      via: "accessibilityIdentifier on each hotspot"
      pattern: "bodymap.region"
---

<objective>
Replace the discrete region-tile grid inside `BodyMapView` with an accessible human-silhouette pain
map matching the approved Pencil design "Endo-App — Body Map Pain Selection". A gender-neutral
front/back silhouette renders as a SwiftUI `Path`; tappable region hotspots sit anatomically over it.
Only the view internals change — every behavioral and accessibility contract the existing tests drive
is preserved byte-for-byte.

Purpose: Ship the approved visual design while keeping the SYMP-02 accessibility contract, the public
binding API, and all four accessibility-identifier families intact so no test regresses.
Output: A rewritten `Sources/EndoApp/Features/DayLog/BodyMapView.swift` that builds clean and keeps
every `bodymap.*` identifier resolving.
</objective>

<execution_context>
@$HOME/.claude/gsd-core/workflows/execute-plan.md
@$HOME/.claude/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@./CLAUDE.md

# The view to rewrite (current tile-grid implementation — the contract reference)
@Sources/EndoApp/Features/DayLog/BodyMapView.swift

# DO NOT MODIFY — read only for the region model + reused control + call site
@Sources/EndoApp/Domain/BodyRegion.swift
@Sources/EndoApp/Features/DayLog/SeverityControl.swift

# The XCUITest the new view MUST keep satisfying (lines ~57-68)
@Tests/EndoAppUITests/LoggingFlowUITests.swift
</context>

<tasks>

<task type="auto">
  <name>Task 1: Rewrite BodyMapView as an accessible silhouette pain map</name>
  <files>Sources/EndoApp/Features/DayLog/BodyMapView.swift</files>
  <action>
Replace ONLY the `LazyVGrid` of `regionTile(...)` with a silhouette + anatomically-placed accessible
hotspots. Keep everything else identical (the SYMP-02 contract): the `@Binding var severities` API,
the `side` state + `Side` enum, the Front/Back `Picker` (`bodymap.sideToggle`),
`frontRegions`/`backRegions` membership, the `.sheet(item: $editingRegion)` severity sheet with
`SeverityControl` + "Clear this area" (`bodymap.region.<raw>.clear`) + "Done"
(`bodymap.region.<raw>.done`), the "All areas (list)" DisclosureGroup (`bodymap.listFallback`, rows
`bodymap.list.<raw>`), the `severitySheetBinding`/`valueText`/`accessibilityValue`/`tint(for:)`
helpers, and the `extension BodyRegion: Identifiable`. Do NOT touch BodyRegion.swift,
SeverityControl.swift, PainPoint, the Repository, or the DayLogEditorView call site.

REQUIRED SYMBOL NAMES (the Task 1 gate greps for these EXACT literal identifiers — produce source that
matches them): the static silhouette path constant MUST be named `bodyPath`; the per-region
normalized-center function MUST be named `hotspotCenter`; the silhouette container MUST use
`.aspectRatio(200.0/440.0, contentMode: .fit)` written exactly that way; the hotspot layout MUST use a
`GeometryReader`; tap MUST set `editingRegion = region`; and each hotspot MUST carry
`.accessibilityAddTraits(.isButton)`. The old `LazyVGrid` tile grid MUST be fully removed (the gate
fails if `LazyVGrid` still appears anywhere in the file).

SILHOUETTE PATH: Add a `static let bodyPath: Path` (named exactly `bodyPath`) in a `viewBox 0 0 200 440`
space. Front AND back use the SAME path. Transcribe the SVG `d` faithfully: arcs (`a`) to
`addArc`/`addRelativeArc`, cubics (`C`) to `addCurve`:
M 79 26 a 21 23 0 1 0 42 0 a 21 23 0 1 0 -42 0 Z M 92 46 C 92 54 88 59 78 62 C 60 67 50 76 47 92 C 45 104 44 122 42 144 C 40 164 38 186 36 204 C 35 214 39 220 45 220 C 51 220 54 214 55 204 C 57 186 60 164 62 146 C 63 132 64 120 65 108 C 66 102 68 98 70 100 C 69 118 68 134 70 150 C 71 162 67 174 65 190 C 63 206 64 220 66 236 C 68 260 70 286 72 310 C 73 330 74 352 75 372 C 76 392 76 406 75 416 C 74 424 78 428 85 428 C 92 428 95 424 95 416 C 95 400 96 380 96 360 C 96 330 97 300 98 272 C 99 260 100 252 100 248 C 100 252 101 260 102 272 C 103 300 104 330 104 360 C 104 380 105 400 105 416 C 105 424 108 428 115 428 C 122 428 126 424 125 416 C 124 406 124 392 125 372 C 126 352 127 330 128 310 C 130 286 132 260 134 236 C 136 220 137 206 135 190 C 133 174 129 162 130 150 C 132 134 131 118 130 100 C 132 98 134 102 135 108 C 136 120 137 132 138 146 C 140 164 143 186 145 204 C 146 214 149 220 155 220 C 161 220 165 214 164 204 C 162 186 160 164 158 144 C 156 122 155 104 153 92 C 150 76 140 67 122 62 C 112 59 108 54 108 46 Z

LAYOUT: Between the Picker and the DisclosureGroup, put a `GeometryReader` sized with
`.aspectRatio(200.0/440.0, contentMode: .fit)` (written exactly that way) so the body fits the available
width, preserves aspect ratio, and is not clipped. In a `ZStack`, draw the scaled `bodyPath` first (fill
`Color(.secondarySystemBackground)`, thin `Color.secondary.opacity(0.3)` stroke), then overlay one
hotspot per region in `regionsForSide`. Scale the path into the proxy size with a `CGAffineTransform`
(scaleX = size.width/200, scaleY = size.height/440) or a custom `Shape` drawn into the rect.

HOTSPOT TABLE (single source of truth; each (x,y) in 200x440 space, used normalized as
(x/200, y/440); view point = `CGPoint(x: nx*size.width, y: ny*size.height)`). EXACTLY ONE element per
BodyRegion. `.shoulder` / `.upperThighs` / `.hips` show two visual dots in the design but have ONE
case — render ONE dot at the single center below (one visual dot == one a11y element; do NOT add a
second decorative dot mistakable for a second element). Element center MUST land here so the
center-tapping XCUITest hits the right spot:
FRONT (side == .front):
  .shoulder (0.500, 0.155) | .chestDiaphragm (0.500, 0.239) | .lowerAbdomenPelvis (0.500, 0.398)
  .lowerQuadrantLeft (0.380, 0.420) | .lowerQuadrantRight (0.620, 0.420) | .bladder (0.500, 0.482)
  .upperThighs (0.500, 0.595)
BACK (side == .back):
  .lowerBack (0.500, 0.364) | .hips (0.500, 0.445) | .rectalBowel (0.500, 0.518)
Implement as `static func hotspotCenter(for region: BodyRegion) -> UnitPoint` (named exactly
`hotspotCenter`, normalized 0...1) with an EXHAUSTIVE switch over all ten cases (compiler proves every
region is placed).

HOTSPOT VIEW: `private func hotspot(_ region: BodyRegion, in size: CGSize) -> some View`. With
`severity = severities[region]`: a filled `Circle` ~22pt tinted by `tint(for: severity)` when marked,
else a faint outlined `Circle` (clear fill, stroke `Color.secondary.opacity(0.4)`). When marked,
CENTER a numeric badge over the dot — `Text("\(severity!)")`, `.caption2.bold().monospacedDigit()`,
`Color.accentColor` (or `.primary`) — so severity is NEVER color-only. Wrap the dot in
`.frame(width: Self.minTarget, height: Self.minTarget)` (>= 44pt) + `.contentShape(Circle())` so the
EFFECTIVE tap area is >= 44pt though the visible dot is ~22pt (overlapping 44pt frames between
neighbours like the lower quadrants are fine as long as each element CENTER is over its own point).
Position via `.position(x: center.x, y: center.y)` from the normalized point. Tap sets
`editingRegion = region` (a `Button { editingRegion = region } label:` with `.buttonStyle(.plain)`, or
`.onTapGesture { editingRegion = region }`).

ACCESSIBILITY (load-bearing — copy the modifier set from the old `regionTile`): on each hotspot apply
`.accessibilityAddTraits(.isButton)`,
`.accessibilityIdentifier("bodymap.region.\(region.rawValue)")`,
`.accessibilityLabel(Text(region.plainLabel))`,
`.accessibilityValue(Text(accessibilityValue(for: region)))`. Reuse the existing
`accessibilityValue(for:)` ("severity N of 10" / "not marked") and `tint(for:)` helpers VERBATIM — do
not change their strings (CopyAuditTests / the XCUITest depend on them). Introduce NO new user-facing
copy: labels come from `BodyRegion.plainLabel`; the only new on-screen text is the bare severity
number (data, not audited chrome). Keep `Self.minTarget = 44`. Update the leading doc comment to
describe the silhouette while keeping the contract wording accurate.
  </action>
  <verify>
    <automated>cd /Users/hannessuhr/Projects/endo-app/.claude/worktrees/jovial-rubin-23cae8 && SRC=Sources/EndoApp/Features/DayLog/BodyMapView.swift && for p in 'GeometryReader' 'hotspotCenter' 'bodyPath' 'aspectRatio' 'editingRegion = region' 'accessibilityAddTraits(.isButton)'; do grep -qF "$p" "$SRC" || { echo "MISSING: $p"; exit 1; }; done && ! grep -qF 'LazyVGrid' "$SRC" && echo "OK: silhouette present, tile grid removed"</automated>
  </verify>
  <done>
BodyMapView.swift renders a silhouette `Path` (named `bodyPath`) in a `GeometryReader`/`ZStack` sized by
`.aspectRatio(200.0/440.0, contentMode: .fit)`, with one accessible hotspot per region (exactly one
element per case) positioned via `hotspotCenter`, tap sets `editingRegion`, marked dots carry a
visible numeric badge + tint, hit areas are >= 44pt via a 44pt frame + contentShape, and the Picker /
severity sheet / list fallback are byte-for-byte unchanged. The old `LazyVGrid` tile grid is removed.
The `@Binding severities` API and the call site are untouched. The Task 1 gate passes only when the
silhouette tokens are present AND `LazyVGrid` is gone (it cannot false-pass on the unmodified file).
  </done>
</task>

<task type="auto">
  <name>Task 2: Regenerate project, build for simulator, verify identifiers survive, run the bodymap UI test</name>
  <files>Sources/EndoApp/Features/DayLog/BodyMapView.swift</files>
  <action>
Verification-only (no source edits unless the build or UI test fails on the code change). Regenerate the
Xcode project, build for an iOS Simulator destination, prove the `bodymap.*` identifier families plus
the toggle/fallback ids still appear in the rewritten source, then run the single LoggingFlowUITests
method that drives the at-risk center-tap contract (taps `bodymap.region.lowerAbdomenPelvis` → severity
sheet → `.done`).

Boot the simulator if needed. If `iPhone 16` is NOT an installed simulator, run
`xcrun simctl list devices available`, substitute an available iPhone simulator name in BOTH the
`xcodebuild build` destination AND the `xcodebuild test` destination, and note the substitution in the
SUMMARY.

If the build fails, fix the compile errors in `BodyMapView.swift` ONLY (do NOT touch BodyRegion,
SeverityControl, PainPoint, Repository, project.yml, or the call site) and rebuild until green.
Identifiers that MUST remain present: `bodymap.region.<raw>`, `bodymap.region.<raw>.done`,
`bodymap.region.<raw>.clear`, `bodymap.list.<raw>`, `bodymap.sideToggle`, `bodymap.listFallback`.

The UI test runs LAST (only after build + identifier grep are green). A GENUINE assertion failure on a
`bodymap.*` identifier (e.g. the center tap misses the region, or the sheet/`.done` id does not resolve)
is a REAL regression and MUST be fixed in `BodyMapView.swift` — re-check the `hotspotCenter` table and
the identifier strings. ONLY if the UI test fails for an environmental reason unrelated to the code
change (simulator boot/install failure, not an assertion on a `bodymap.*` identifier) may the executor
record the build gate + identifier grep as the binding proof and note the UI-test environment issue in
the SUMMARY.
  </action>
  <verify>
    <automated>cd /Users/hannessuhr/Projects/endo-app/.claude/worktrees/jovial-rubin-23cae8 && xcodegen generate && xcodebuild -project EndoApp.xcodeproj -scheme EndoApp -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16' build CODE_SIGNING_ALLOWED=NO 2>&1 | tail -3 && SRC=Sources/EndoApp/Features/DayLog/BodyMapView.swift && for id in 'bodymap.region.' '.done"' '.clear"' 'bodymap.list.' 'bodymap.sideToggle' 'bodymap.listFallback'; do grep -qF "$id" "$SRC" && echo "OK: $id" || { echo "MISSING: $id"; exit 1; }; done && xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppUITests/LoggingFlowUITests CODE_SIGNING_ALLOWED=NO 2>&1 | tail -5</automated>
  </verify>
  <done>
`xcodegen generate` succeeds and `xcodebuild ... build` finishes BUILD SUCCEEDED (Swift 6 / iOS 18, no
new dependencies); all six identifier checks print OK — the four `bodymap.*` families plus
`bodymap.sideToggle` and `bodymap.listFallback` survive the rewrite; and `xcodebuild test
-only-testing:EndoAppUITests/LoggingFlowUITests` passes (the center tap on
`bodymap.region.lowerAbdomenPelvis` opens the severity sheet and `.done` resolves). If the simulator is
unavailable and the UI test fails ONLY for environmental reasons (not a `bodymap.*` assertion), the
build gate + identifier grep stand as the binding proof and the environment issue is noted in the
SUMMARY.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| user tap -> view state | A tap selects an enumerated `BodyRegion`; only that enum and a clamped `0...10` severity ever reach `severities`. No free-form input. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-erj-01 | Tampering | hotspot -> `severities` | accept | Only enumerated `BodyRegion` keys + `0...10` severities (clamped in `severitySheetBinding`) are written; no untrusted input path. |
| T-erj-02 | Information disclosure | on-device pain data | accept | Render-only change; data stays in the existing on-device draft + Repository path — no new persistence, network, or logging. |
| T-erj-03 | Denial of service | silhouette `Path` | accept | Static finite path authored at compile time; no user-controlled geometry, recursion, or unbounded loop. |
| T-erj-SC | Tampering | npm/pip/cargo installs | accept | No package installs — the constraint forbids new dependencies; nothing to audit. |
</threat_model>

<verification>
- `xcodegen generate` regenerates `EndoApp.xcodeproj` clean.
- `xcodebuild ... -scheme EndoApp ... build CODE_SIGNING_ALLOWED=NO` reports BUILD SUCCEEDED (Swift 6 / iOS 18, no new deps).
- All six identifier families still appear in BodyMapView.swift.
- `xcodebuild test -only-testing:EndoAppUITests/LoggingFlowUITests` passes (center-tap on `bodymap.region.lowerAbdomenPelvis` → severity sheet → `.done`), unless the simulator is environmentally unavailable (then build + identifier survival are the binding proof, noted in the SUMMARY).
- `BodyMapView.swift` is the ONLY changed source file; BodyRegion.swift, SeverityControl.swift, PainPoint, Repository, project.yml, and the call site are untouched.
- One accessibility element per BodyRegion with `.isButton`, the plain label, and the "severity N of 10" / "not marked" value.
</verification>

<success_criteria>
- The body map shows a tappable human silhouette (front/back) instead of the tile grid.
- Each visible region maps to exactly one accessible hotspot whose center is over the intended anatomical spot with a >= 44pt effective hit area.
- Marked regions show a numeric badge + severity tint (never color-only); unmarked regions show a faint outlined dot.
- Tapping a hotspot opens the existing severity sheet; Clear/Done and the list fallback still work.
- The `@Binding severities` API, the call site, and CopyAuditTests/BodyMapBindingTests are unchanged; the build is green.
- `LoggingFlowUITests` is actually run by Task 2's gate and passes (center-tap → sheet → `.done`). If the simulator is unavailable, this claim is scoped to "build green + all six identifier families survive in BodyMapView.swift", with the UI-test environment issue noted in the SUMMARY.
</success_criteria>

<output>
Create `.planning/quick/260615-erj-replace-bodymapview-region-tile-grid-wit/260615-erj-SUMMARY.md` when done.
</output>
