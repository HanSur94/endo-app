# Phase 19: App Shell & Consent - Context

**Gathered:** 2026-07-09
**Status:** Ready for planning
**Mode:** Auto-generated (decisions pre-locked in ANDROID-PORT-PLAN.md + ARCHITECTURE.md; UI direction fixed by the shipped iOS "Bloom" design system)

<domain>
## Phase Boundary

A user can complete onboarding/consent and land in a Bloom-styled 3-tab Compose shell — and health UI cannot exist before consent, BY CONSTRUCTION. Delivers ASHL-01..04: the consent-by-construction `AppGate`, the Bloom design-system port (`designsystem` package: colors, typography, components, tab chrome), the 3-tab shell (Log / Insights / Explore) with edge-to-edge + predictive back, onboarding/consent flow, and the manifest assertion test proving NO `INTERNET` permission. This phase renders the shell and gate; the actual tracking/insights/explore SCREEN content arrives in Phases 20/22 (this phase provides tab scaffolds/placeholders that the gate protects).

</domain>

<decisions>
## Implementation Decisions

Architecture LOCKED in [.planning/research/ANDROID-PORT-PLAN.md](../../research/ANDROID-PORT-PLAN.md) §5 (consent-by-construction invariant) and [.planning/research/ARCHITECTURE.md](../../research/ARCHITECTURE.md) (gate = `when`-switch, no nav library); UI norms in [.planning/research/FEATURES.md](../../research/FEATURES.md) (edge-to-edge, predictive back); pins in [.planning/research/STACK.md](../../research/STACK.md) (Compose BOM 2026.06.00, Material 3, activity-compose). Do not re-open. Key decisions:

### Navigation & gate (consent-by-construction)
- Single-activity `:app` hosting `BloomTheme { AppGate() }`. `AppGate` is a plain three-way `when`-switch on `ConsentState` (Unknown/loading → splash; NotConsented → onboarding/consent; Consented → the tab shell). NO navigation library — the health/tab composables are UNCONSTRUCTIBLE in the pre-consent branch (they are only referenced inside the `Consented ->` arm). Navigation 3 is the recorded escape hatch if stacked navigation ever appears; do NOT add it now.
- The consented subtree is only composed post-consent. Withdrawing consent (from Settings, added here or Phase 20) flips `ConsentState` and re-gates the whole shell. Deep links / notification taps land on the gate, never inside it (Phase 20/22 wire the actual deep-link targets; here the gate simply can't be bypassed).
- Consume `ConsentStore` + `EndoRepository` from Phase 18 (repository already refuses health access pre-consent — this phase adds the UI-level gate on top).

### 3-tab shell
- Material 3 `NavigationBar` with 3 destinations (Log / Insights / Explore), state hoisted to a `selectedTab` remember/StateFlow (not a NavHost). Each tab shows a Bloom-styled scaffold/placeholder in THIS phase; Phase 20 fills Log, Phase 22 fills Insights + Explore.
- Edge-to-edge from day one (`enableEdgeToEdge()`, insets handled via `WindowInsets`/`Scaffold`) and predictive back default-on — both mandatory at targetSdk 36; retrofitting later is the expensive path (research pitfall). Handle system-bar insets so content doesn't draw under bars incorrectly.

### Bloom design-system port (`designsystem` package)
- Port the iOS Bloom tokens to a Compose theme: `BloomColors` (canvas, surface, surfaceSunken, card, ink/inkSecondary/inkMuted, rose/roseDeep, plum/plumDeep, gold, petal/petalLine, strokeSubtle, teal, indigo) as a Compose `ColorScheme` + a custom `BloomColors` holder via `CompositionLocal` (Material 3's ColorScheme doesn't have slots for all brand tokens); `BloomTypography` (display/title/H2/H3/eyebrow) as a Compose `Typography` + text styles; `BloomComponents` (card/component styling) as composables/modifiers; the floating-capsule tab chrome as the `NavigationBar` styling.
- Do NOT use Material You dynamic color (would recolor semantically-meaningful Bloom tokens — research anti-feature). Provide the Bloom light theme; a dark theme can follow later (not required for parity this phase unless trivially derivable).

### Copy discipline (from Phase 18)
- Every user-facing string introduced here (onboarding, consent, tab labels, placeholders) MUST be registered in the Phase-18 `CopyAudit` catalog IN THE SAME CHANGE, so the CopyAudit JVM test scans it. This is the first phase to add real UI copy — the harness exists; use it. Consent/onboarding copy must pass ClaimsPolicy (no diagnostic/alarmist/gendered/imperative/false-precision language) and carry the "wellness tool, not a medical device / informational, not medical advice" framing consistent with iOS.

### Manifest / privacy
- `:app` manifest declares NO `INTERNET` permission (nor any health WRITE). Add a `ManifestPermissionsTest` (JVM/merged-manifest or resource scan) asserting no INTERNET permission — the Data-safety form's strongest artifact (research). Keep the manifest minimal; Health Connect permissions arrive in Phase 21.

### Consent semantics
- Port the iOS consent content (`Consent/ConsentState.swift`, `ConsentView.swift`, `Onboarding/OnboardingView.swift`) faithfully: informed consent for special-category health data, on-device + no-account framing, the ability to decline. Persist via the Phase-18 `ConsentStore` (DataStore). Consent must be an explicit affirmative action, not a default.

### Claude's Discretion
- Exact Compose package layout under `com.endoapp.app.ui` + `com.endoapp.app.designsystem`, whether tabs use a sealed `Tab` enum, the splash/loading treatment for `ConsentState.Unknown`, and the onboarding step count/pacing — Claude's discretion, guided by the iOS OnboardingView/ConsentView and the UI-SPEC this phase generates.
- Whether shell UI tests use Robolectric (JVM, fast) or instrumented `createComposeRule` — prefer Robolectric where it can prove the gate invariant on the JVM (no emulator); instrumented UI tests are the main-only CI lane.

</decisions>

<code_context>
## Existing Code Insights

### Port source (read-only)
- iOS Bloom: `Sources/EndoApp/DesignSystem/BloomColors.swift`, `BloomTypography.swift`, `BloomComponents.swift`, `BloomTabBar.swift` (+ `BloomTabBarAppearance`).
- iOS shell/gate: `App/AppGate.swift` (consent gate → TabView with 3 ConsentedTab: log/insights/explore), `App/AppDependencies.swift`.
- Consent/onboarding: `Consent/ConsentState.swift`, `ConsentStore.swift`, `Onboarding/OnboardingView.swift`, `Features/Onboarding/ConsentView.swift`.
- Claims/framing copy: `Content/ClaimsPolicy.swift`, `Content/Disclaimer.swift`, `Content/PrivacyPolicyText.swift`.

### Established from Phases 17–18 (reuse)
- `:app` module (Compose-ready: add Compose BOM + activity-compose + material3 to the catalog if not already present), `EndoRepository` + `ConsentStore` (DataStore, 3-state ConsentState: Unknown/NotConsented/Consented), the `CopyAudit`/`AuditableCopy` harness (register new copy here), the version-catalog audit + `:insights` boundary-lint (keep green). Android SDK (platform 36) installed; `./gradlew :app:assembleDebug` builds for real.

### Integration points
- Phase 20 (tracking) fills the Log tab + adds a Settings entry (incl. consent withdrawal + reminders); Phase 22 fills Insights + Explore tabs; Phase 21 adds Health Connect entry. This phase leaves clearly-marked tab scaffolds for them.

</code_context>

<specifics>
## Specific Ideas

The consent-by-construction invariant is the signature carry-over: prove with a Compose/Robolectric test that no health/tab composable can be reached in the NotConsented branch, and that flipping consent off re-gates. The manifest no-INTERNET test is the Data-safety anchor. Match the iOS Bloom look closely (rose/plum palette, floating-capsule tab bar).

</specifics>

<deferred>
## Deferred Ideas

- Actual Log/Insights/Explore screen CONTENT → Phases 20 (Log) and 22 (Insights + Explore). This phase renders Bloom-styled tab scaffolds only.
- Health Connect connect entry + permissions → Phase 21.
- Biometric app lock / recents-thumbnail hiding → post-parity (Future Requirements APRV-01/02), not this phase.
- Dark theme → later polish unless trivially derivable from the Bloom tokens.

</deferred>
