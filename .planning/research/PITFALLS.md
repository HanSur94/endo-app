# Pitfalls Research

**Domain:** Free, privacy-first iOS endometriosis tracking + on-device insights app (SwiftUI / HealthKit / SwiftData + CloudKit private sync)
**Researched:** 2026-06-03
**Confidence:** HIGH for Apple-policy / HealthKit / CloudKit technical pitfalls (Apple docs + developer forums); HIGH for regulatory framing (MDCG/MDR guidance, GDPR Art. 9); MEDIUM for insights-trust / clinical-UX pitfalls (peer-reviewed survey evidence + community sources)

> Reading order for the roadmap author: the four pitfalls flagged **[FOUNDATIONAL]** below are cheap to bake in wrong now and expensive-to-impossible to fix after launch (cycle data model, CloudKit schema, the iCloud-health-storage policy line, and insights trust). Treat them as gating design decisions for Phase 1–2, not polish.

---

## Critical Pitfalls

### Pitfall 1: Assuming a regular ~28-day cycle in the data model and prediction engine  **[FOUNDATIONAL]**

**What goes wrong:**
The cycle model is built around "cycle = bleed-to-bleed, ~28 days, predict next period by averaging." Endometriosis patients frequently have irregular cycles, and a large share are on continuous hormonal treatment (combined pill back-to-back, progestin-only, GnRH agonists/antagonists, IUD) that suppresses or abolishes menstruation entirely. A 28-day-centric model produces nonsense predictions, shows a "cycle phase" for someone with no cycle, and silently breaks correlation features that key off cycle day.

**Why it happens:**
Every tutorial, sample schema, and competitor calendar UI models the textbook cycle. It's the path of least resistance and looks correct in demos with fake regular data.

**How to avoid:**
- Model the **bleeding/treatment state as first-class and independent of "cycle"**: support states like *naturally cycling*, *hormonally suppressed (no expected bleed)*, *breakthrough bleeding*, *post-surgical / menopausal*, *unknown*. Let the user declare their situation in onboarding and change it anytime.
- Make cycle-day a **derived, optional, confidence-scored** attribute — never a required column other data hangs off. Insights must function on absolute dates and symptom history alone when no reliable cycle exists.
- Predictions must **degrade gracefully**: when variance is high or the user is suppressed, switch from "your period starts in 3 days" to "no reliable prediction — your cycles vary a lot" or hide the feature. Never show false precision.
- Anchor correlations on **calendar time + logged events**, not assumed cycle phase, so the engine still works for suppressed users (your differentiator must serve them too).

**Warning signs:**
- Schema has a non-optional `cycleDay` / `cyclePhase` field, or a `cycleLength` defaulting to 28.
- Any code path divides by cycle length or assumes a "next period" date always exists.
- Test fixtures only contain regular cyclers; no fixture for "suppressed, no bleeding for 8 months."

**Phase to address:**
Phase 1 (data model) and the insights phase. This is the single most expensive thing to retrofit because CloudKit schema is effectively append-only after launch (see Pitfall 5).

---

### Pitfall 2: The App Store iCloud-health-storage rule vs. the CloudKit sync architecture  **[FOUNDATIONAL]**

**What goes wrong:**
App Store Review Guideline **5.1.3(ii)** states: *"Apps must not write false or inaccurate data into HealthKit… and may not store personal health information in iCloud."* The entire planned architecture is "on-device + private iCloud (CloudKit) sync" of menstrual and symptom data. A naive reading — and some reviewers' reading — is that this is an outright rejection. Discovering this at submission, after the sync layer is built, is a near-catastrophe.

**Why it happens:**
The guideline is widely quoted out of context ("Apple bans iCloud for health apps"). The real intent targets dumping HealthKit-derived data into general iCloud storage (iCloud Documents/Drive, key-value store) where it is *Apple-readable* or leaves the user's private scope. **CloudKit *private database*** — end-to-end-/account-encrypted, scoped to the user's own iCloud, never shared, gated behind explicit consent and a Privacy Manifest CloudKit reason — is the accepted pattern, and is how Apple's own Health app syncs across devices. But the line is real and reviewer-dependent.

**How to avoid:**
- **Do not copy HealthKit-imported samples into CloudKit verbatim.** Treat HealthKit as a read-source; store the *user's own logged data* (which is your app's data, not "HealthKit data") in the CloudKit **private** database. Keep HealthKit-origin values out of synced storage, or re-derive them on each device from HealthKit locally.
- Use **`NSPersistentCloudKitContainer` private database only** — never public/shared. Add the **CloudKit reason in the Privacy Manifest** and a clear in-app consent + explanation of where data lives.
- Make sync **opt-in with explicit consent copy** ("Your data is stored only in your private iCloud, end-to-end protected, never on our servers") — this both satisfies GDPR explicit consent (Pitfall 4) and gives a clean answer to App Review.
- **Pre-validate with a TestFlight build and, if uncertain, a pre-submission review note** to App Review describing the private-encrypted-CloudKit design. Have a fallback: local-only mode if sync is rejected.

**Warning signs:**
- Code path writes `HKSample`-derived values straight into the synced store.
- Using the public or shared CloudKit database for anything health-related.
- No Privacy Manifest CloudKit reason; no in-app explanation of storage location.

**Phase to address:**
Phase 1 architecture decision + the sync phase. Resolve the policy interpretation **before** building sync, ideally with a throwaway TestFlight submission to de-risk.

---

### Pitfall 3: Over-confident "flare prediction" and correlation→causation insights eroding trust  **[FOUNDATIONAL]**

**What goes wrong:**
The insights engine is the stated differentiator, so there's pressure to surface bold claims: *"Gluten triggers your flares,"* *"Flare likely tomorrow."* With sparse, noisy, self-reported data these are often spurious (multiple-comparisons false positives, confounding, reverse causation — feeling bad makes you eat/sleep differently). Survey evidence shows inaccurate predictions are the #1 driver of frustration and abandonment in cycle apps, and for people who suffer severely (endo), a wrong "you're fine" or a scary false alarm is actively harmful to trust. Once a user catches the app being confidently wrong, the whole product loses credibility.

**Why it happens:**
Confident, causal language is more compelling and demos better than honest hedging. Naive correlation scans over many factors *will* find significant-looking links by chance. ML/stats on n=30 noisy logs feels like signal.

**How to avoid:**
- **Language discipline:** say *"possible association,"* *"observed alongside,"* *"pattern worth discussing with your doctor"* — never *"trigger,"* *"causes,"* *"will."* Show this is the user's own data, not medical fact.
- **Statistical guardrails:** require a minimum sample/observation count before showing any correlation; correct for multiple comparisons; show effect size + a plain-language confidence; suppress low-confidence findings rather than ranking them.
- **Show your work / falsifiability:** let users see the underlying data behind a claim and dismiss/"this isn't right" to teach the model and build trust.
- **Frame predictions as ranges and uncertainty**, and *withhold* a prediction when confidence is low (ties into Pitfall 1's graceful degradation).
- Add an explicit **"not medical advice"** boundary around every insight (ties into Pitfall 6).

**Warning signs:**
- Insight copy contains causal verbs ("triggers," "causes," "prevents").
- Correlations shown with <~2–3 cycles / small n, or no multiple-comparison correction.
- No confidence indicator; no way for the user to inspect or reject a finding.
- Predictions always produce an answer instead of sometimes saying "not enough data."

**Phase to address:**
Insights phase — but the data model (Phase 1) must capture enough structured, timestamped, dismissable signal to support honest stats later. Build the "uncertainty + dismiss" UX into the first insights release, not as a v2 fix.

---

### Pitfall 4: Treating special-category health data (GDPR Art. 9) as ordinary data  **[FOUNDATIONAL for legal posture]**

**What goes wrong:**
Menstrual + symptom + diagnosis data is **special-category personal data under GDPR Art. 9** (and the user/audience is largely EU/German). Art. 9 *prohibits* processing such data unless a specific exception applies — for a consumer app the realistic basis is **explicit consent**, layered on top of an Art. 6 lawful basis. Shipping without explicit, specific, withdrawable consent, a real privacy policy, and data-subject rights (export, delete) is a legal exposure even though everything is "on-device."

**Why it happens:**
"It never leaves the device, so GDPR doesn't apply" is a common but wrong assumption — processing on the user's device under your control still counts, and CloudKit sync plus any future crash/analytics tooling clearly does. Privacy-by-design is assumed to be automatic with on-device storage; the *paperwork and consent flow* get skipped.

**How to avoid:**
- **Explicit consent UX** before any health processing/sync: specific, opt-in, plain-language, withdrawable; record consent.
- **Data-subject rights baked in:** full export (see Pitfall 10) and a real, complete **delete** (local store *and* the CloudKit private DB — test that delete actually purges the cloud copy).
- **A genuine privacy policy** linked in App Store Connect *and* in-app (Apple also requires this), accurately describing on-device + private-iCloud storage and the absence of servers/third parties.
- **No third-party analytics/ad/attribution SDKs** touching health data (this is also App Store 5.1.2(vi)/5.1.3(i) — see Pitfall 7). Privacy Manifest + "Data Not Collected" should be truthful.
- Keep the on-device + no-account architecture as the privacy moat — but document it as a deliberate Art. 9 minimization strategy, not an excuse to skip consent.

**Warning signs:**
- Onboarding asks for data before any consent screen.
- "Delete my data" only clears local store; CloudKit copy survives.
- Any SDK (Firebase, Sentry with PII, ad/attribution) in the dependency list.
- Privacy policy is a placeholder.

**Phase to address:**
Phase 1 (consent + privacy posture) and the sync phase (delete-purges-cloud verification). Cheap now, reputationally/legally expensive later.

---

### Pitfall 5: CloudKit schema rigidity + sync conflict/data-loss — "data loss on a health app is catastrophic"  **[FOUNDATIONAL]**

**What goes wrong:**
Once a SwiftData/Core Data + CloudKit schema is pushed to the **Production** environment, it is effectively **append-only**: you cannot rename or delete entities/attributes or change types without CloudKit treating it as delete+add — silently losing the old data and breaking sync across devices. SwiftData additionally **doesn't expose conflict-resolution customization** (last-writer-wins is largely what you get), and **custom migration hooks (`willMigrate`/`didMigrate`) are not called when CloudKit is enabled**. For a health journal users may maintain for years, partial sync, dropped relationships, or a botched migration = lost months of pain logs = product death.

**Why it happens:**
SwiftData + CloudKit "just works" in the happy-path demo, hiding these constraints. Developers iterate the schema freely in dev, then discover the rules only when production data starts disappearing on the second device.

**How to avoid:**
- **Design the schema defensively up front** (compounds with Pitfalls 1 & 4): all CloudKit-synced attributes **optional or with defaults**; **no `@Attribute(.unique)`** (CloudKit has no cross-device uniqueness); relationships modeled carefully; favor additive evolution (new optional fields, version flags) over renames.
- Adopt an **explicit schema-versioning strategy** and `VersionedSchema`/migration plans **knowing custom hooks won't fire under CloudKit** — keep migrations lightweight/additive only.
- **Conflict strategy:** assume last-writer-wins; avoid models where two devices editing the same record loses meaningful data (e.g. prefer append-only log entries over a single mutable "today" record).
- **Test the multi-device + offline path explicitly:** two devices, airplane mode, conflicting edits, fresh install restoring from iCloud, and a schema upgrade with existing production-style data. Verify relationships and new fields actually appear on the second device.
- **Local backup / export as a safety net** (export feature doubles as disaster recovery) and never block the user's local writes on sync availability.

**Warning signs:**
- Any non-optional CloudKit attribute without a default; any `.unique` macro.
- Schema renamed/retyped after a production push.
- No two-device + offline test; testing only on simulator with one account.
- Single mutable record per day instead of append-only events.

**Phase to address:**
Phase 1 (schema) is the make-or-break point; sync phase must include the multi-device/offline/migration test matrix as explicit success criteria.

---

### Pitfall 6: Blurring the "wellness tool" / regulated-medical-device (MDR / DiGA) line

**What goes wrong:**
Under EU MDR (and MDCG borderline guidance, incl. MDCG 2019-11 rev.1 and 2025-4), software acquires a **medical purpose** — and thus becomes a regulated medical device requiring CE marking/conformity assessment — when it's intended for *diagnosis, prediction, prognosis, treatment, or monitoring of a disease*. An app that says it **predicts flares**, **detects/diagnoses** patterns, gives **treatment recommendations**, or uses "clinically validated"/"evidence-based" claims it hasn't substantiated can be pulled across the line into being an unregistered medical device — exactly the regulated, expensive DiGA-style path the project explicitly wants to avoid. The reference product (German Endo-App) *is* a CE-marked DiGA precisely because it makes therapeutic claims.

**Why it happens:**
The differentiator is "insights / flare prediction / recommendations" — language that sounds medical. Marketing copy and feature names drift toward clinical claims because they're compelling. The MDR boundary is genuinely fuzzy and inconsistently applied, so it's easy to assume "we're just a tracker."

**How to avoid:**
- **Position as informational self-tracking, not medical advice.** Frame insights as *the user's own data reflected back* ("patterns in *your* logs"), never as diagnosis, prognosis, or treatment guidance.
- **Audit all user-facing + marketing language** for medical-purpose verbs: avoid "diagnose," "detect [disease]," "treat," "clinically proven," and reframe "predict flares" as "spot patterns / possible early warnings in your own history" with explicit uncertainty (ties to Pitfall 3).
- **Pervasive "not medical advice / consult your clinician" framing** — onboarding, insights, reports, settings.
- **Don't claim clinical validation** you can't evidence; don't imply equivalence to the DiGA.
- If a future feature genuinely needs to *monitor/diagnose*, treat that as a scope/regulatory decision, not a sprint task.

**Warning signs:**
- Feature/marketing copy uses "diagnose," "detect," "treat," "medical-grade," "clinically validated."
- Recommendations phrased as instructions ("take X," "stop Y") rather than discussion prompts.
- "Flare prediction" presented as a reliable clinical alert rather than a personal-pattern heuristic.

**Phase to address:**
Cross-cutting — establish a **language/claims policy** in Phase 1 and enforce it at every UI-copy and App Store metadata review. Especially gate the insights and report phases.

---

### Pitfall 7: HealthKit read-permission opacity and partial authorization

**What goes wrong:**
For **read** access, HealthKit deliberately **does not tell you whether the user denied permission** — denied reads look identical to "no data exists." `authorizationStatus(for:)` is only meaningful for *write/share* types. So an app that asks for many categories and gets partial grants will silently show empty charts / broken insights for denied types, with no way to detect *why*, and may misread "user denied" as "user has no symptoms." Requesting a huge bundle of types at first launch also triggers permission fatigue and blanket denial.

**Why it happens:**
Developers assume read auth behaves like other iOS permissions (queryable status). The privacy-preserving opacity is surprising and under-documented in tutorials.

**How to avoid:**
- **Don't gate logic on read-auth status** — assume you may get nothing and design empty/"no data yet" states that don't imply the user is symptom-free.
- **Request HealthKit access incrementally and contextually** (when the user enters a feature that needs that data type), with clear in-app rationale *before* the system sheet, rather than one giant prompt at launch.
- **Provide manual logging as a first-class path** so the app is fully usable even if HealthKit is entirely denied — never make HealthKit a hard dependency.
- For data you *write*, you *can* check status — use that to drive UX where possible.
- Detect "permission likely denied" only via behavioral signals (consistently empty + user expectation), and offer a deep link to Settings, never a false "you have no symptoms" conclusion.

**Warning signs:**
- Code branches on `authorizationStatus` for read types and assumes "authorized."
- One mega-permission prompt at first launch.
- App unusable / blank without HealthKit grants.
- Empty HealthKit query treated as "user is healthy / has no entries."

**Phase to address:**
HealthKit integration phase; onboarding/permission UX phase.

---

### Pitfall 8: Non-inclusive, gendered, or stigmatizing language

**What goes wrong:**
Endometriosis affects people who are **not all cis women** — trans men and non-binary people have endo, and many are specifically on hormonal treatment to suppress menstruation (which intersects with Pitfall 1). An app drenched in "women's health," pink-everything, "your lady cycle," and assumptions that everyone menstruates alienates a meaningful, already-underserved part of the audience and undermines the "open to everyone worldwide" positioning. It also harms cis women who are suppressed/post-surgical and *don't* menstruate.

**Why it happens:**
The entire category defaults to gendered, period-centric branding; it's the unexamined norm and "looks normal" to most builders.

**How to avoid:**
- Use **"people with endometriosis," "you," symptom-first** framing; avoid "women," "ladies," gendered cycle metaphors in core flows.
- **Decouple identity from cycle state** — don't assume the user menstruates (onboarding from Pitfall 1 already captures suppressed/no-bleed states).
- Inclusive, non-stigmatizing visual design (avoid forced pink/floral-only).
- Let the user describe their own situation rather than picking from a cis-centric template.
- Review copy against inclusive-language guidance (EndoFound, Endometriosis Network Canada, Clue).

**Warning signs:**
- Copy/onboarding assumes monthly bleeding or addresses "women/ladies."
- No path for "I don't currently menstruate."
- Visuals lock into a single gendered aesthetic.

**Phase to address:**
Cross-cutting copy/design system, set in Phase 1 onboarding and enforced everywhere.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hardcode 28-day cycle / required `cycleDay` | Faster MVP, simple prediction | Breaks for irregular + suppressed users; CloudKit schema can't be cleanly fixed post-launch (Pitfall 5) | **Never** for this domain |
| Copy HealthKit samples directly into the CloudKit synced store | Less mapping code | App Store 5.1.3 risk + duplicate/contradictory data | Never — re-derive from HealthKit per device or keep separate |
| Non-optional / `.unique` CloudKit attributes | Cleaner local model | Sync failures, silent data loss, no path back | Never with CloudKit enabled |
| Ship insights with causal/confident copy and no min-sample gate | More impressive demo | Trust collapse on first wrong claim; nudges toward MDR medical-device line | Never |
| Add Firebase/Sentry/ads "just for crash logs/metrics" | Easy observability | Breaks Art. 9 + App Store 5.1.2(vi)/5.1.3(i); contradicts privacy positioning | Only fully on-device, health-data-excluded, opt-in diagnostics |
| One mega HealthKit permission prompt at launch | One-time setup | Blanket denial + silent empty data you can't diagnose | Never; request contextually |
| "Delete" that only clears the local store | Quick to build | GDPR violation + user data resurrects from iCloud on reinstall | Never |
| Test sync on a single device/simulator only | Fast | Multi-device conflict/loss bugs ship to users; catastrophic for a health log | Never for the sync phase |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **HealthKit (read)** | Branching on `authorizationStatus` for read types; treating empty query as "no symptoms" | Assume opacity; design "no data" states; manual-log fallback; request contextually with pre-prompts |
| **HealthKit + iCloud** | Storing HealthKit-derived data in iCloud/CloudKit (5.1.3(ii)) | Keep HealthKit as a local read-source; sync only the app's own user-entered records in the **private** CloudKit DB with consent + Privacy Manifest |
| **SwiftData + CloudKit** | Renaming/retyping/deleting fields after production push; `.unique`; non-optional fields; expecting custom migration hooks | Additive-only schema, all optional/defaulted, no unique, lightweight migrations only; version flags |
| **CloudKit conflict** | Expecting to customize conflict resolution in SwiftData | Assume last-writer-wins; model append-only events instead of single mutable records |
| **App Store privacy** | "Data Not Collected" label while a third-party SDK sees health data | Truthful Privacy Manifest; zero analytics/ad SDKs touching health data |
| **Swift Charts** | Plotting cycle-phase axis assuming regular cycles | Plot against absolute dates/events; handle gaps and suppressed periods gracefully |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Re-running full correlation scan over entire history on every app open | UI jank, battery drain, slow insights tab | Incremental/cached computation; recompute on new data only; background compute | After ~1–2 years of daily logs |
| Loading all log records into memory for charts | Memory pressure, scroll stutter | Aggregate/window queries; date-range fetches; chart downsampling | Hundreds–thousands of entries |
| CloudKit full-sync churn on large histories | Battery, network, sync delays | Batch operations; rely on incremental NSPersistentCloudKitContainer sync; avoid mass rewrites | Multi-year multi-device users |

*(Scale here is "years of one user's daily data + a couple of devices," not server scale — over-engineering for millions of users is itself a trap given the no-backend constraint.)*

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Storing health data in general iCloud / unencrypted exports | Exposure of Art. 9 data; App Store rejection | Private CloudKit DB only; encrypt/permission-scope exports; no iCloud Drive dumps |
| Bundling analytics/ad/attribution SDKs | Health data leaves device; legal + policy violation | None — exclude them entirely from a health build |
| Export/share producing an unprotected file with full health history | Leaked PDF/CSV in cloud drives, chat, email | Minimize export scope, user-selected ranges, on-device generation, share-sheet (no auto-upload) |
| Logging health values to console/crash reports | PII in logs / third-party crash dashboards | Never log health values; redact; no PII in crash tooling |
| No app-level lock | Shoulder-surfing / shared-device exposure of stigmatized data | Offer optional Face ID/passcode app lock |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Notification fatigue (daily nags, false flare alerts) | Annoyance → notifications off → abandonment; scary false alarms harm trust | Few, high-value, user-configurable reminders; never push speculative "flare likely" alerts; respect quiet hours |
| Heavy, high-friction logging | Users in pain/fatigue won't log → sparse data → bad insights → churn | Fast, low-tap logging; sensible defaults; quick "same as yesterday"; HealthKit import to cut double-entry |
| Inaccessible to users in pain/fatigue/migraine | VoiceOver-broken charts, tiny fixed text, low contrast exclude exactly the worst-day users | Full Dynamic Type, VoiceOver labels on charts/controls, high contrast, large tap targets, one-handed reach |
| Confident wrong predictions (Pitfall 3) | Trust collapse | Uncertainty-first copy; withhold low-confidence outputs |
| Gendered/period-assuming flows (Pitfall 8) | Alienation of trans/non-binary/suppressed users | Inclusive, symptom-first, decoupled-from-cycle language |
| Doctor report that looks flimsy/unstructured | Clinicians dismiss it; user loses the one high-stakes payoff | Clean, structured, dated, source-labeled (self-reported vs HealthKit), concise summary + trends, "self-tracked, not diagnostic" note |

## "Looks Done But Isn't" Checklist

- [ ] **Cycle model:** works for a user who has *never bled* in the app's history (suppressed) — no phantom "phase," no crash, insights still function.
- [ ] **CloudKit sync:** verified on **two real devices + offline edits + reinstall-from-iCloud**, with relationships and a newly added field appearing on the second device.
- [ ] **Delete-my-data:** purges the **CloudKit private DB**, not just local — confirmed by reinstall on a clean device showing nothing.
- [ ] **HealthKit denied:** app fully usable; denied reads don't render as "no symptoms"; manual logging path intact.
- [ ] **Insights:** every claim has a min-sample gate, a confidence indicator, non-causal language, and a "this isn't right" dismiss.
- [ ] **Copy audit:** no "diagnose/detect/treat/clinically proven"; pervasive "not medical advice"; inclusive, non-gendered framing.
- [ ] **Privacy:** real privacy policy linked in-app + App Store; explicit Art. 9 consent before processing; Privacy Manifest truthful; **zero** analytics/ad SDKs touching health data.
- [ ] **Export:** doctor report is structured, source-labeled, generated on-device, shared via share-sheet (no silent upload).
- [ ] **Accessibility:** Dynamic Type at largest sizes, VoiceOver on charts, contrast — tested, not assumed.
- [ ] **Notifications:** opt-in, minimal, configurable; no speculative flare-prediction pushes.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| 28-day assumption baked into CloudKit schema | **HIGH** | New optional fields/version flag for bleeding/treatment state (can't rename old); migration/cleanup logic; may need a model-version bump and data backfill — far worse than designing right initially |
| iCloud-health-storage rejection at submission | **HIGH** | Re-architect to keep HealthKit data local + private-CloudKit only for app data; add consent + Privacy Manifest; resubmit with review note; ship local-only fallback meanwhile |
| CloudKit schema mistake post-production (rename/unique) | **HIGH** | Often unrecoverable cleanly — new container/schema + migration path; risk of user data loss; emphasizes prevention |
| Insights lost user trust after a confident wrong claim | **MEDIUM** | Add uncertainty UX + min-sample gates; rewrite copy; in-app explanation; but reputation damage lingers |
| Missing GDPR consent / delete | **MEDIUM** | Add explicit consent flow + true cloud-purging delete + export; update privacy policy |
| HealthKit logic gated on read-auth | **LOW–MEDIUM** | Remove status branches; add "no data" states + manual fallback |
| Notification fatigue / gendered copy | **LOW** | Tune notification policy; copy/design pass |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. 28-day assumption | Phase 1 (data model) + insights | Suppressed-user fixture passes; no phantom phase; cycleDay is optional/derived |
| 2. iCloud health-storage policy | Phase 1 architecture + sync phase | TestFlight/App Review note clears; HealthKit data not in synced store; Privacy Manifest CloudKit reason present |
| 3. Over-confident insights | Insights phase (data model enables it) | Every insight gated by sample size + confidence + non-causal copy + dismiss |
| 4. GDPR Art. 9 / consent | Phase 1 + sync phase | Explicit consent before processing; delete purges cloud; no health-touching SDKs; policy live |
| 5. CloudKit schema/conflict/data-loss | Phase 1 schema + sync phase | Two-device + offline + reinstall + additive-migration test matrix passes |
| 6. Wellness vs medical-device line | Phase 1 claims policy, enforced cross-cutting | Copy/metadata audit: no medical-purpose verbs; "not medical advice" pervasive |
| 7. HealthKit read opacity / partial auth | HealthKit + onboarding phases | App usable with all reads denied; contextual prompts; empty ≠ "healthy" |
| 8. Inclusive language | Phase 1 onboarding + design system | Copy audit; "I don't menstruate" path exists; non-gendered visuals |
| 9. Notification fatigue | Notifications/UX phase | Opt-in, minimal, configurable; no speculative flare pushes |
| 10. Doctor-report credibility / safe export | Report/export phase | Structured, source-labeled, on-device, share-sheet; "self-tracked" disclaimer |

## Sources

- App Store Review Guidelines — Apple Developer (5.1.2(vi), 5.1.3(i), 5.1.3(ii) health-data / iCloud / advertising restrictions): https://developer.apple.com/app-store/review/guidelines/  *(HIGH — primary source, quoted verbatim)*
- "Apple bans iCloud and 7 other rules for HealthKit developers" — MobiHealthNews (context on the iCloud-storage rule): https://www.mobihealthnews.com/news/apple-bans-icloud-and-7-other-rules-healthkit-developers  *(MEDIUM)*
- Protecting user privacy / Authorizing access to health data — Apple Developer (HealthKit read-auth opacity): https://developer.apple.com/documentation/healthkit/protecting-user-privacy ; https://developer.apple.com/documentation/healthkit/authorizing-access-to-health-data  *(HIGH)*
- HealthKit Authorization Status discussions (read vs write status opacity): https://cocoacasts.com/more-about-managing-permissions-with-healthkit ; https://payalkandlur.medium.com/healthkit-authorization-status-d069d01615c6  *(MEDIUM, corroborating)*
- "Designing Models for CloudKit Sync" & initializeCloudKitSchema — fatbobman (SwiftData/Core Data + CloudKit schema rules, optional/no-unique, additive-only): https://fatbobman.com/en/snippet/rules-for-adapting-data-models-to-cloudkit/ ; https://fatbobman.com/en/snippet/resolving-incomplete-icloud-data-sync-in-ios-development-using-initializecloudkitschema/  *(MEDIUM–HIGH, well-known iOS data author + corroborated by Apple forums)*
- Apple Developer Forums — SwiftData CloudKit conflict resolution & migration-hook limitations: https://developer.apple.com/forums/thread/751480 ; https://developer.apple.com/forums/thread/744491  *(MEDIUM)*
- EU MDR / MDCG borderline & software classification (MDCG 2019-11 rev.1, MDCG 2025-4; medical-purpose test) — European Commission, NAMSA, BfArM, Emergo by UL: https://health.ec.europa.eu/medical-devices-topics-interest/software-and-apps_en ; https://namsa.com/resources/blog/eu-mdr-and-ivdr-classifying-medical-device-software-mdsw/ ; https://www.bfarm.de/EN/Medical-devices/Tasks/Differentiation-and-classification/_artikel.html  *(HIGH for the regulatory principle)*
- GDPR Art. 9 special-category data + explicit-consent condition — gdpr-info.eu, ICO: https://gdpr-info.eu/art-9-gdpr/ ; https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/lawful-basis/special-category-data/  *(HIGH)*
- Period-tracker prediction accuracy & user frustration (irregular/endo cycles) — Broad/Biswakarma/Harper 2022 survey, Worsfold et al. 2021: https://journals.sagepub.com/doi/10.1177/17455057221095246 ; https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9047811/  *(MEDIUM — peer-reviewed survey evidence)*
- Inclusive-language / endo in trans & non-binary people — EndoFound, Endometriosis Network Canada, Clue, Frontiers in Medicine review: https://www.endofound.org/how-gendered-language-harms-non-binary-endometriosis-patients ; https://endometriosisnetwork.com/endo-hub/drop-gendered-language/ ; https://www.frontiersin.org/journals/medicine/articles/10.3389/fmed.2024.1430154/full  *(MEDIUM)*

---
*Pitfalls research for: free privacy-first iOS endometriosis tracking + on-device insights app*
*Researched: 2026-06-03*
