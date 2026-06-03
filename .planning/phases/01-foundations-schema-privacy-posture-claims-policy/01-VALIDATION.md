---
phase: 1
slug: foundations-schema-privacy-posture-claims-policy
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-03
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source: `01-RESEARCH.md` §Validation Architecture. Every Phase-1 requirement is provable **without a paid Apple account** (Swift Testing against an in-memory `ModelContainer` + an `xcodebuild` Simulator build).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`@Test` / `#expect`), Xcode 16+/Swift 6 |
| **Config file** | none — test target `EndoAppTests` declared in `project.yml` (created in Wave 0) |
| **Quick run command** | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests/<Suite>` |
| **Full suite command** | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Build-only check** | `xcodebuild -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' build` |
| **Estimated runtime** | ~30–60 s (small unit suite) |

> Substitute a real Simulator name from `xcrun simctl list devices available`. All commands require full Xcode active (see Wave 0).

---

## Sampling Rate

- **After every task commit:** Run the relevant `-only-testing:` quick run for the file touched
- **After every plan wave:** Run the full `xcodebuild test` suite (must be green)
- **Before `/gsd-verify-work`:** Full suite green **and** a successful `xcodebuild ... build` for the Simulator destination
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

> Task IDs are assigned by the planner (Step 8). Rows below are the requirement-level proofs from RESEARCH.md; the planner maps each to a task ID and wave. All start as Wave-0 (test files do not exist yet).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | 01 | 0/1 | DATA-01 | — | CloudKit-safe schema (all optional, no `.unique`, inverses) | unit | `xcodebuild test ... -only-testing:EndoAppTests/SchemaRoundTripTests` | ❌ W0 | ⬜ pending |
| TBD | 01 | 0/1 | DATA-01 | — | "Suppressed, no bleeding" fixture persists, no phantom cycle field | unit | `... -only-testing:EndoAppTests/SchemaRoundTripTests` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | ONBD-01 | T-consent | Processing blocked until `ConsentState == .consented`; `withdraw()` re-gates | unit | `... -only-testing:EndoAppTests/ConsentGateTests` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | ONBD-03 | — | Neutral default terminology; customization changes resolved copy; no gendered defaults | unit | `... -only-testing:EndoAppTests/TerminologyTests` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | ONBD-02 | — | "Not medical advice" disclaimer present & surfaced by onboarding | unit | `... -only-testing:EndoAppTests/DisclaimerTests` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | ONBD-04 | — | Privacy text non-empty, states on-device + private iCloud + no third-party sharing; reachable from onboarding + Settings | unit | `... -only-testing:EndoAppTests/PrivacyPolicyTests` | ❌ W0 | ⬜ pending |
| TBD | 01 | 1 | (skeleton) | — | App builds & launches for Simulator destination | build | `xcodebuild ... build` (exit 0) | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] **Install/select full Xcode** (`sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`) — **blocking, manual** (App Store download; cannot be automated)
- [ ] **`brew install xcodegen`** — **blocking** for `.xcodeproj` generation
- [ ] Generate the project: `xcodegen generate` from `project.yml`
- [ ] Confirm an iOS 18+ Simulator runtime + iPhone device exist (`xcrun simctl list devices available`)
- [ ] `Tests/EndoAppTests/SchemaRoundTripTests.swift` — DATA-01 (incl. suppressed fixture)
- [ ] `Tests/EndoAppTests/ConsentGateTests.swift` — ONBD-01
- [ ] `Tests/EndoAppTests/TerminologyTests.swift` — ONBD-03
- [ ] `Tests/EndoAppTests/DisclaimerTests.swift` + `PrivacyPolicyTests.swift` — ONBD-02/04

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Install full Xcode + select toolchain | (Wave 0) | App Store download requires user action; not scriptable | Install Xcode, run `sudo xcode-select -s`, confirm `xcodebuild -version` |
| App launches showing consent gate, writes+reads one record after consent | DATA-01, ONBD-01 | End-to-end skeleton proof in Simulator | Build & run in Simulator; grant consent; confirm one record round-trips |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter (after planner aligns task IDs)

**Approval:** pending
