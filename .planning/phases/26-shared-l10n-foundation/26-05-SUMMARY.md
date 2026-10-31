---
phase: 26-shared-l10n-foundation
plan: 05
subsystem: l10n
tags: [l10n, glossary, register, audit, tripwire, swift, ios, safe-01, safe-02, safe-03, safe-04]

requires:
  - phase: 26-04
    provides: "PolicyLanguage, L10nText, LanguageClaimsPolicy + RuleKinds, strict decode, ClaimsPolicy.forLanguage"
provides:
  - "Sources/EndoApp/Content/L10n/Glossary.swift — strict glossary model, register/concept banned matchers, conformance() verdicts"
  - "Tests/EndoAppTests/L10nSourceRegistry.swift — path-pattern registry, repoRoot, glossary, auditFindings, tripwire walk"
  - "Tests/EndoAppTests/L10nAuditTests.swift — registered-string audit, 4 red controls, 5 tripwire cases"
  - "Tests/EndoAppTests/GlossaryConformanceTests.swift, AllowListHygieneTests.swift — 16 cases"
affects: [27, 29, 30, 33]

actuals:
  tokens: 52000
  tasks: 3
  commits: 3
ios_branch: claude/endo-ios-l10n
ios_head: db19b74
ios_commits: [ac4c0ad, c4722b5, db19b74]
l10n_tree_ios: e54fe59035a253760b81a3eb0e02cb092a33e2f5
l10n_tree_android: e54fe59035a253760b81a3eb0e02cb092a33e2f5
simulator: "iPhone 17 (iOS 26.5)"
plan_head_before: 646c9a8
requirements-completed: [SAFE-01, SAFE-02, SAFE-03, SAFE-04]

tech-stack:
  added: []
  patterns:
    - "Registration IS the enforcement: one test-side registry claims every localized path, extracts COPY strings and exempts documentation with a reason; the tripwire fails on any unclaimed localized file"
    - "One auditFindings helper serves the real scan and every red control, so a control that turns red proves the real scan would too"
    - "Register checks compare cased tokens scalar-wise on the NFC typography view (never a case-folded form)"

key-files:
  created:
    - Sources/EndoApp/Content/L10n/Glossary.swift
    - Tests/EndoAppTests/L10nSourceRegistry.swift
    - Tests/EndoAppTests/L10nAuditTests.swift
    - Tests/EndoAppTests/GlossaryConformanceTests.swift
    - Tests/EndoAppTests/AllowListHygieneTests.swift
  modified: []

key-decisions:
  - "Patterns are stored as String and evaluated with String.range(of:options:.regularExpression) + a full-range check: Swift 6 does not treat Regex as Sendable, so a static Regex cannot live in a Sendable registry"
  - "Red capability is proven by four live controls (seeded mustFail sentences, mutated DE framing copy, mutated DE approved form, mid-sentence register break) plus one planted-file tripwire proof, instead of committing throw-away stub implementations that would be deleted in the next commit"
  - "The conformance suite ports the pinning cases (shared pairs, register edges, strict decode, coverage, approved∩banned, canonical terms, EN triggers, masking toggle) rather than all 13 Kotlin tests; the shared selfTest pairs remain the cross-platform invariant"
  - "Full verification ran the unit suites with -skip-testing:EndoAppUITests; the UI suite hangs on a cold simulator on this machine (documented gap, same as 26-04)"

verification:
  - "Task 1 tracer: L10nAuditTests 5 tests green (>=30 strings per language, zero findings, four red controls)"
  - "Task 2: 21 tests in 3 suites green (audit 5, conformance 10, allow hygiene 6)"
  - "Task 3: L10nAuditTests 10 tests green incl. five tripwire cases"
  - "Live red proof: planted Sources/EndoApp/Resources/de.lproj/Localizable.strings made repoHasNoUnregisteredLocalizedFile fail (exit 65); file removed, tree clean"
  - "Full iOS unit suite: 318 EndoApp + 156 EndoInsights tests green"
  - "SAFE-04 guard: additions only under Sources/Tests/EndoInsights; ClaimsPolicy.swift, EndoInsights and the three English test files unchanged"
  - "Android full suite (:insights:test + :app:testDebugUnitTest) green in AND_WT"
  - "Cross-branch identity: git diff --quiet claude/endo-android-version-e608a7 claude/endo-ios-l10n -- l10n; both tree objects = e54fe59035a253760b81a3eb0e02cb092a33e2f5"
  - "iOS branch is local only: no upstream configured, no push, no PR"

port-bugs:
  - "androidResDir was first written with an escaped interpolation in a raw string, so the Android BCP-47 qualifier never matched; the tripwire case tripwireFlagsUnknownLocalizedFiles caught it (values-b+es+419) and it was fixed"
  - "registerMaskingCanBeDisabled initially asserted every DE allow token collides with the register; the real collision for behandlungsteam is policy-only, so the case now uses the FR euphonic phrase 'a t il'"
