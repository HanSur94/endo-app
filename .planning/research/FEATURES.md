# Feature Research

**Domain:** Endometriosis & menstrual/symptom tracking + insights (free, privacy-first iOS app)
**Researched:** 2026-06-03
**Confidence:** HIGH for table-stakes & endo symptom model (multiple clinical + product sources, Apple docs); MEDIUM for the insights-engine sub-capabilities (synthesized from competitor behavior, not internal docs); MEDIUM for flare prediction (genuinely hard, see PITFALLS).

## Benchmark Summary

| Product | Type | Core strength | Insights depth | Report export | Notable gap (our opportunity) |
|---------|------|---------------|----------------|---------------|-------------------------------|
| **Endo-App** (German DiGA) | Prescription DTx | Multimodal therapy modules + specialized endo-diary; "Endo-Plan" + SOS plan | Diary *evaluations* feed a therapy plan; not a correlation/trigger engine | Yes — evaluations usable in doctor consults | Insights are therapy-routing, not "what triggers MY flares." Prescription-gated, German-only, 90-day course. **We out-insight it and are free/global.** |
| **Phendo** (Columbia/Citizen Endo) | Research app | Best endo-specific symptom model (momentary + day-level; pain locations, GI/GU, dyspareunia, daily-function "Great→Unbearable"); has controls (non-endo users) | "Insight" tab = bleeding patterns, daily experience, tracking activity over 30 days (descriptive, not predictive/correlational) | Limited | Data goes to *researchers*, not back to the user as personalized insight. **We close the loop: insights for the person.** |
| **Luna for Health** | Endo app (freemium) | LunaEndoScore, medical record, PDF for consults, validated quizzes | Score-based; light on personal correlations | Yes — PDF summary | Insight is a one-time-ish score, not longitudinal trigger discovery. |
| **Bearable** | General symptom tracker | **Best-in-class insights**: "Impacts"/correlation grid, factors↔symptoms over time, experiments | Correlations after ~7d; trigger ranking after ~30d; custom experiments | Weekly reports | Generic (not endo-shaped), best insights paywalled. **We bring Bearable-grade insights to an endo-native model, free.** |
| **Flo** | Mainstream period tracker | Predictions, huge content library, cycle-phase mapping | Cycle-phase symptom mapping, predictions | Limited | **2021 FTC complaint: shared health data with Facebook/Google.** Trust liability — our anti-model. |
| **Clue** | Mainstream period tracker | Clinically-validated predictions, **irregular-cycle tolerant**, BC modes, 6 hormonal subphases, inclusive | Analysis tab (flow/cramps), cycle-phase patterns; correlations modest | Limited | Good cycle engine + inclusivity to emulate; weak on cross-factor trigger discovery. |

**Strategic read:** Table stakes = Phendo's symptom model + Clue's irregular-tolerant cycle engine. Differentiator = Bearable-grade insights, but endo-native and free, with the loop closed back to the user (not a research silo like Phendo, not a therapy router like the DiGA). Anti-model = Flo's data monetization.

## Feature Landscape

### Table Stakes (Users Expect These)

Missing these = the app feels like a generic period tracker or an incomplete diary; endo users churn to Phendo/Bearable.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Endo-specific symptom logging** (full set below) | Endo users explicitly reject generic period apps; pain/GI/dyspareunia are the disease | **M** | The differentiator vs a period tracker. Model enumerated in next section. Depends on data model + entry UI. |
| **Pain severity + location body map** | Pain location matters clinically (bowel/bladder/pelvic phenotypes); Endolog/Phendo/Endo-App all do it | **M** | Tappable body/pelvic map + 0–10 (or NRS) severity per region. Higher value than a single global pain score. |
| **Bleeding / menstrual flow logging** | Core of any cycle tool; also a symptom for endo (heavy flow = severe phenotype) | **S** | Flow level + spotting/intermenstrual bleeding. Maps to HealthKit `menstrualFlow`/`intermenstrualBleeding`. |
| **Cycle tracking & prediction, irregular-tolerant** | Expected of any cycle app; endo cycles are irregular / suppressed | **L** | MUST degrade gracefully: no textbook 28-day assumption; support "no cycle / on continuous hormonal Tx" mode. See ARCHITECTURE/PITFALLS. Depends on bleeding log. |
| **Mood / emotional state logging** | Standard across all benchmarks; mood is an endo symptom and a confound | **S** | Simple scale + optional tags (anxious, low, irritable). |
| **Fatigue / energy logging** | One of the most reported endo symptoms beyond pain | **S** | 0–N scale; HealthKit `fatigue` exists. |
| **GI / "endo belly" logging** | Bloating, nausea, constipation/diarrhea, dyschezia — hallmark endo cluster | **S** | Group as a GI sub-section. HealthKit has `bloating`, `nausea`, `constipation`, `diarrhea`, `abdominalCramps`. |
| **Medication & hormonal treatment logging** | Needed for both self-mgmt and doctor reports; Phendo maps 64 options; confounds insights | **M** | Meds (incl. PRN painkillers), hormonal therapy (pill/IUD/GnRH), supplements. Doses + adherence optional. |
| **Daily / reminder-based logging flow** | Adherence is the whole game; benchmarks optimize "a few taps" | **M** | Fast daily check-in; smart defaults; local notification reminders. Adherence feeds insight quality. |
| **Food / nutrition logging** | In PROJECT scope; users hunt for dietary triggers (low-FODMAP, gluten, dairy, caffeine, alcohol) | **M** | Lightweight tag-based ("had dairy", "caffeine", "alcohol", "processed") beats a full calorie DB for trigger-finding. HealthKit nutrition import optional. |
| **Physical activity / sport logging** | In PROJECT scope; exercise is both trigger and relief; pull from HealthKit workouts | **S–M** | Type + intensity/duration; auto-import from HealthKit workouts reduces double-logging. |
| **Trends & charts over time** | Every benchmark visualizes history; "is it getting worse?" is the #1 user question | **M** | Swift Charts: symptom severity over time, flow calendar, frequency. |
| **Cycle-phase symptom mapping** | Clue/Flo/Oura standard: "how do I feel across my cycle" | **M** | Overlay symptoms on cycle phases; degrade to calendar/heatmap when no cycle. Depends on cycle engine + symptom log. |
| **Doctor-ready PDF report export** | In PROJECT scope; Luna/Endolog/CareClinic/EndoTracker all ship it; #1 reason endo users track | **M** | Clean PDF: date range, pain distribution, symptom frequency/severity, flow trend, meds overview, wellness score. Share sheet. |
| **HealthKit integration (read + selective write)** | In PROJECT scope; reduces double-logging; iOS users expect it | **M** | Read cycle/sleep/workouts/nutrition; consider writing symptoms back. Depends on data model. |
| **Private, no-account data ownership** | Post-Dobbs/post-Flo-FTC, privacy is now a table-stakes *expectation*, not just a differentiator | **M** | On-device + private CloudKit, no login. Must be *visibly* communicated (privacy is also a differentiator below). |
| **Customizable / "track only what's relevant" set** | Endo presentations vary (4 phenotypes); forcing all fields kills adherence | **M** | Let users enable/disable symptom categories. Also serves inclusive design (disable fertility/pregnancy framing). |
| **Inclusive, non-assumptive design** | Not all users identify as women or menstruate (continuous Tx); pink/fertility framing causes dysphoria & alienation | **S–M** | Gender-neutral copy, neutral palette, ability to hide pregnancy/fertility & cycle-prediction, no "your period is late!" nags. Mostly copy/IA discipline, low code cost, high trust payoff. |

### Differentiators (Competitive Advantage)

This is where we beat the DiGA and the generic trackers. The **insights engine is the centerpiece** — broken into buildable sub-capabilities.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Insights: correlation / trigger discovery** (symptoms ↔ food, sleep, activity, cycle phase, meds) | The core promise: "what makes MY endo worse?" Bearable's killer feature, but endo-native & free | **L** | Compute lagged associations (e.g., dairy on day N → bloating day N/N+1). Show ranked "factors linked to your flares." Needs ~7–30d data before surfacing. Statistical rigor + plain-language framing is the hard part (see PITFALLS: spurious correlation, confounding). |
| **Insights: trend & cycle-phase mapping with charts** | "My fatigue clusters in the late luteal phase" — actionable pattern recognition | **M** | Heatmaps/phase overlays; works in calendar mode when no cycle. Builds on table-stakes charts; the *interpretation layer* is the differentiator. |
| **Insights: flare prediction / early warning** | "Tomorrow looks high-risk based on your pattern" — proactive, unique vs all benchmarks | **L** | Hardest & riskiest. Start with simple personal heuristics (cycle-phase risk + recent trigger exposure) before any ML. MUST be framed as informational, never diagnostic. High research-flag (see PITFALLS). Depends on correlation engine + cycle engine. |
| **Insights: personalized, data-driven recommendations** | "You tend to feel better on days you walk + sleep 7h+" — closes the loop the DiGA/Phendo don't | **L** | Derive from the user's *own* positive/negative correlations. Must avoid medical-advice framing ("you tend to report..." not "you should..."). Depends on correlation engine. |
| **Insights confidence / "more data needed" honesty** | Trust differentiator: most apps over-claim; we under-promise & show data sufficiency | **S–M** | Show n, confidence, "need X more days." Directly counters spurious-correlation harm. Cheap, high-trust. |
| **Endo-native symptom model with body map & momentary + day-level logging** | Phendo-grade depth, but the data feeds the *user's own* insights, not a research silo | **M–L** | Momentary (a flare now) + daily summary, like Phendo. This is both table-stakes depth and a differentiator vs generic trackers. |
| **Privacy-by-architecture as a feature** (on-device, private iCloud, no account, no ads/analytics SDK) | Post-Flo-FTC / post-Dobbs, "we literally cannot see or sell your data" is a strong, marketable wedge | **M** | Architectural, but surface it in UX: privacy explainer, "your data never leaves your devices." Genuine differentiator vs Flo/most freemium apps. |
| **Doctor report that's genuinely clinical-grade** | Beyond a data dump: phenotype-aware summary (pain map, cyclicity, severity trend, function score) | **M** | Lean into endo-clinical framing (cyclical vs constant pain, dyspareunia, dyschezia/dysuria, function). Higher value than competitors' generic PDFs. Depends on report export + symptom model. |
| **Free, global, no prescription** | The whole reason to exist vs the DiGA (prescription-gated, German, 90-day) | n/a | Positioning, not a build. Reinforced by no-backend/no-cost architecture. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Ad / analytics / attribution SDKs on health data** | Standard monetization; "we need usage metrics" | This is the Flo FTC scandal. Special-category GDPR data; destroys the privacy wedge; legal exposure | No third-party SDKs on health data. If any telemetry, on-device/aggregate-only & opt-in, no health content. |
| **Medical-diagnosis / "you have endometriosis" claims** (cf. Luna's "diagnosis" framing) | Users want answers; sounds authoritative | Regulated medical-device territory (DiGA/CE/FDA); legal risk; PROJECT explicitly out-of-scope | Informational insights only: "you tend to report..."; persistent "not medical advice / see a clinician" framing. |
| **Cloud account / custom backend / web app** | "Sync everywhere", "log in on the web" | Recurring server cost (breaks free model); creates a breachable health-data honeypot; login friction | Private CloudKit sync across the user's own Apple devices, no account. (PROJECT decision.) |
| **Social feed / community / sharing** | Endo is isolating; peer support is real | Moderation burden, abuse/privacy risk, off-core for v1, can leak sensitive data | Out of scope for v1 (PROJECT). Revisit only post-validation; if ever, keep it firewalled from tracking data. |
| **Fertility / "trying to conceive" / pregnancy-mode as default** | Many cycle apps lead with this | Many endo users aren't TTC, are on suppressive Tx, or find it alienating/dysphoria-inducing | Optional, off by default; never nag. Supports inclusive design. |
| **Aggressive gamification / streak shaming** | Boosts retention metrics | Punishes the days users are too sick to log (the worst flares = missed logs = broken streaks + guilt) | Gentle, optional encouragement; never penalize gaps; treat missing data as signal, not failure. |
| **Full calorie/macro food database** | "Proper nutrition tracking" | Heavy to build/maintain; calorie precision is irrelevant for trigger discovery; tanks logging speed | Lightweight food-tag taxonomy (dairy, gluten, caffeine, alcohol, FODMAP-ish, processed) — enough for correlations. |
| **Over-confident flare predictions / push alerts** | "Warn me before a flare" | Endo is noisy; false alarms erode trust & cause anxiety; over-claiming flirts with medical-device line | Probabilistic, low-key, opt-in early-warning with confidence + "not a diagnosis"; ship descriptive insights first. |
| **Forcing the full symptom set on everyone** | "Comprehensive tracking" | Endo phenotypes vary (Phendo found 4); long forms kill daily adherence | Customizable enabled set; smart defaults; progressive disclosure. |
| **Generic "women's health" pink-everything branding** | Market convention | Alienates trans/nonbinary users & those not menstruating; signals "generic period app" | Neutral, clinical-but-warm design; gender-neutral copy. |

## Endo-Specific Symptom Model (Concrete)

This is the credibility line between an endometriosis app and a period tracker. Synthesized from Phendo's data model, the clinical endo literature (classic triad + GI/GU + systemic), and HealthKit's available symptom identifiers. Severity on a consistent scale (e.g., 0–10 NRS or none/mild/moderate/severe); each ideally supports cyclicity (worse around menses vs constant).

**Pain (with body-map location + severity):**
- Pelvic / lower-abdominal pain
- Dysmenorrhea (period pain)
- Dyspareunia (pain during/after sex) — pairs with optional sexual-activity log (HealthKit `sexualActivity`)
- Dyschezia (pain on bowel movement)
- Dysuria (pain/burning on urination)
- Lower back pain (HealthKit `lowerBackPain`)
- Leg / radiating pain
- Ovulation / mittelschmerz pain
- Pain *cyclicity* flag (cyclical worsening vs constant/chronic — clinically meaningful for endo)

**GI / "endo belly" cluster:**
- Bloating / "endo belly" distension (`bloating`)
- Abdominal cramps (`abdominalCramps`)
- Nausea (`nausea`) / vomiting (`vomiting`)
- Constipation (`constipation`) / diarrhea (`diarrhea`)
- Appetite changes (`appetiteChanges`)

**Genitourinary:**
- Urinary frequency / urgency
- Bladder pain / interstitial-cystitis-like symptoms

**Bleeding / menstrual:**
- Flow level (`menstrualFlow`)
- Spotting / intermenstrual bleeding (`intermenstrualBleeding`)
- Clots / heavy flow flag
- Period start/end (cycle-start metadata for HealthKit)

**Systemic / other:**
- Fatigue / low energy (`fatigue`)
- Headache / migraine (`headache`)
- Dizziness / fainting (`dizziness`/`fainting`)
- Hot flashes (`hotFlashes` — esp. on GnRH/menopausal Tx)
- Sleep quality (or import from HealthKit `sleepAnalysis`)
- Brain fog / concentration

**Mood / psychological:**
- Mood / emotional state (`moodChanges`)
- Anxiety / low mood (often `mindfulSession`-adjacent; track as tags)

**Function / quality-of-life (Phendo-style):**
- Overall day rating ("Great → Unbearable")
- Daily activities that were hard (work, exercise, social, intimacy) — strong for doctor reports
- Days off work / bed-bound flag

**Contextual factors (inputs for the insights engine, not symptoms):**
- Food tags (dairy, gluten, caffeine, alcohol, high-FODMAP, processed, large meal)
- Activity (type, intensity, duration; HealthKit workouts)
- Sleep (duration/quality; HealthKit)
- Stress level
- Medications taken (scheduled + PRN painkillers), hormonal therapy, supplements
- Weather / barometric pressure (optional; some users report sensitivity)
- Cycle phase (derived) / on-continuous-suppression flag

## Insights Engine — Buildable Sub-Capabilities

The PROJECT's centerpiece. Decomposed from highest-confidence/lowest-risk to hardest, which is also the recommended build order.

1. **Descriptive trends (foundation)** — per-symptom severity/frequency over time, flow calendar, "30-day at a glance" (Phendo-grade). **M.** Pure aggregation over the log. *Ship first.*
2. **Cycle-phase mapping** — overlay symptoms on derived phases; heatmap of "symptom × phase"; calendar fallback when no/suppressed cycle. **M.** Depends on cycle engine + log.
3. **Correlation / trigger discovery** — ranked associations between contextual factors and symptoms, with lag (factor day N → symptom N/N+1), gated by data sufficiency (n, confidence). **L.** The core differentiator. Statistical care required (multiple-comparisons, confounding, base rates) — see PITFALLS. Output as plain-language "factors linked to your flares" + "factors linked to your better days."
4. **Personalized recommendations** — surface the user's *own* protective factors ("you tend to feel better on days you walk + sleep 7h+"). **L.** Derived from #3; strictly "you tend to report…", never prescriptive. Depends on correlation engine.
5. **Flare prediction / early warning** — combine cycle-phase risk + recent trigger exposure + trend momentum into a low-key, probabilistic "today/tomorrow looks higher-risk." **L, highest risk.** Start heuristic (no ML); always show confidence; never diagnostic. Depends on #2 + #3. *Build last; flag for deeper phase research.*
6. **Insight honesty layer** — data-sufficiency indicators, "need X more days," confidence on every claim. **S–M.** Cross-cuts all of the above; a trust differentiator that directly mitigates the spurious-correlation pitfall.

## Feature Dependencies

```
[Endo symptom data model] ──required by──> [Symptom logging UI]
        │                                         │
        │                                         └──required by──> [Trends & charts]
        │                                                                  │
[Bleeding/flow log] ──required by──> [Cycle engine (irregular-tolerant)]   │
        │                                  │                               │
        │                                  └──required by──> [Cycle-phase mapping]
        │                                                          │
[Contextual factor logging                                         │
 (food / activity / sleep / meds)] ──required by──> [Correlation / trigger engine]
                                                          │         │
                                                          ├──required by──> [Personalized recommendations]
                                                          └──+ cycle-phase ──> [Flare prediction]

[HealthKit integration] ──enhances──> [Activity/sleep/nutrition/cycle logging] (reduces double-entry)
[Insight honesty layer] ──enhances──> [Correlation engine, Recommendations, Flare prediction]
[Private CloudKit sync] ──independent of──> [feature logic] (persistence layer; build early, parallelizable)
[Doctor PDF report] ──required by──> [Symptom model + Trends + (optionally) Insights]
[Customizable symptom set] ──enhances──> [Logging UI] (+ serves inclusive design)
```

### Dependency Notes

- **Cycle engine requires bleeding log AND must be optional/suppressible:** prediction degrades to a calendar/heatmap when the user is on continuous hormonal suppression or has no discernible cycle. This is a hard requirement, not a nice-to-have (PROJECT context).
- **Correlation engine requires contextual factor logging:** with only symptoms and no food/activity/sleep/med inputs, there's nothing to correlate against. Factor logging must ship before/with the insights engine.
- **Flare prediction depends on both cycle-phase mapping and the correlation engine:** it's a composition of earlier capabilities — it cannot be built first.
- **Doctor report depends on the symptom model and trends; insights are optional enrichment:** a useful report can ship before the full insights engine, de-risking value delivery.
- **HealthKit enhances but should not be required:** the app must be fully usable for someone who declines HealthKit permissions.
- **Insight honesty layer enhances every insight feature:** it's the guardrail that keeps correlations/predictions from over-claiming.

## MVP Definition

### Launch With (v1)

Minimum to validate "insights make logging worth it" while being a credible endo tool.

- [ ] **Endo-specific symptom logging + pain body map** — credibility vs generic trackers
- [ ] **Bleeding/flow logging** — cycle + symptom foundation
- [ ] **Irregular-tolerant cycle tracking** with suppression/no-cycle mode — non-negotiable for endo
- [ ] **Contextual factor logging** (food tags, activity, sleep, meds, mood, fatigue, stress) — inputs for insights
- [ ] **Fast daily check-in + reminders** — adherence is prerequisite to insight quality
- [ ] **Trends & charts (descriptive insights)** — immediate value before correlation engine matures
- [ ] **Cycle-phase mapping** — differentiated pattern view
- [ ] **Correlation / trigger discovery + insight honesty layer** — the centerpiece differentiator
- [ ] **Doctor-ready PDF report** — top reason endo users track; high retention driver
- [ ] **HealthKit read integration** (workouts/sleep/nutrition/cycle) — reduce double-logging
- [ ] **Private CloudKit sync, no account** — privacy wedge + PROJECT architecture
- [ ] **Inclusive, customizable, non-assumptive design** — trust + adherence, low marginal cost

### Add After Validation (v1.x)

- [ ] **Personalized recommendations** — once correlation engine proves stable & trustworthy on real data
- [ ] **Flare prediction / early warning** — after enough longitudinal data validates personal heuristics; needs dedicated research phase
- [ ] **Custom "experiments"** (Bearable-style A/B on a trigger) — power-user trigger validation
- [ ] **HealthKit write-back** of symptoms — once data model is stable
- [ ] **Richer report customization** (date ranges, phenotype-aware summaries)
- [ ] **Weather/barometric factor** — low-cost optional correlation input

### Future Consideration (v2+)

- [ ] **Education / knowledge library** — explicitly deferred (PROJECT)
- [ ] **Guided therapy programs** (physio/nutrition/pain-coping) — large content + clinical lift; the DiGA's turf (PROJECT)
- [ ] **Android / web** — iOS-first (PROJECT)
- [ ] **Community / peer support** — only if firewalled from health data; high moderation cost
- [ ] **Medical-device/DiGA certification path** — only if positioning ever changes (currently out-of-scope)

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Endo symptom logging + body map | HIGH | MEDIUM | P1 |
| Bleeding/flow logging | HIGH | LOW | P1 |
| Irregular-tolerant cycle engine | HIGH | HIGH | P1 |
| Contextual factor logging | HIGH | MEDIUM | P1 |
| Daily check-in + reminders | HIGH | MEDIUM | P1 |
| Trends & charts (descriptive) | HIGH | MEDIUM | P1 |
| Doctor PDF report | HIGH | MEDIUM | P1 |
| Private CloudKit sync, no account | HIGH | MEDIUM | P1 |
| Inclusive/customizable design | MEDIUM | LOW | P1 |
| HealthKit read integration | MEDIUM | MEDIUM | P1 |
| Cycle-phase mapping | HIGH | MEDIUM | P1/P2 |
| Correlation/trigger engine + honesty layer | HIGH | HIGH | P1/P2 |
| Personalized recommendations | HIGH | HIGH | P2 |
| Flare prediction / early warning | MEDIUM–HIGH | HIGH | P2/P3 |
| Custom experiments | MEDIUM | MEDIUM | P3 |
| HealthKit write-back | LOW–MEDIUM | MEDIUM | P3 |
| Weather/barometric factor | LOW | LOW | P3 |

**Priority key:** P1 = must have for launch · P2 = should have, add when possible · P3 = nice to have / future.

## Competitor Feature Analysis

| Feature | Endo-App (DiGA) | Phendo | Bearable | Clue | Our Approach |
|---------|-----------------|--------|----------|------|--------------|
| Endo symptom depth | High (endo-diary) | **Highest** (pain loc, GI/GU, dyspareunia, function) | Low (generic, customizable) | Low–med (endo "use case") | Phendo-grade model, but feeding the user's own insights |
| Irregular/suppressed cycles | Endo-aware | Tracks bleeding patterns | N/A (generic) | **Strong** (irregular-tolerant) | Clue-grade tolerance + explicit suppression mode |
| Correlation/trigger insights | No (therapy routing) | No (descriptive only) | **Yes** (paywalled) | Modest | Bearable-grade, endo-native, **free**, with honesty layer |
| Cycle-phase mapping | Limited | Limited | Limited | **Yes** (6 subphases) | Yes; degrades to calendar when no cycle |
| Flare prediction | No | No | No | No | **Unique** (v1.x, heuristic-first, non-diagnostic) |
| Personalized recommendations | Therapy-plan based | No | Partial (impacts) | No | From user's own correlations; non-prescriptive |
| Doctor report export | Yes (evaluations) | Limited | Weekly reports | Limited | **Clinical-grade**, phenotype-aware PDF |
| Privacy model | Clinical/regulated | Research servers (HIPAA) | Commercial | Better-than-most | **On-device + private iCloud, no account, no SDKs** |
| Cost / access | Prescription, German, 90d | Free (research) | Freemium | Freemium | **Free, global, no prescription** |
| Inclusive design | Clinical | Has non-endo controls | Neutral | **Strong (LGBTQIA+)** | Gender-neutral, non-assumptive, customizable |

## Sources

- Endo-App (DiGA): https://endometriose.app/en/endo-app/ · https://www.diga-verzeichnis.de/en/diga/endo-app · https://femtechinsider.com/endo-app-diga-2/
- Phendo / Citizen Endo: https://citizenendo.org/phendo/ · https://apps.apple.com/us/app/phendo/id1145512423 · Phenotypes paper (npj Digital Medicine 2020): https://pmc.ncbi.nlm.nih.gov/articles/PMC7314826/ · https://www.nature.com/articles/s41746-020-0292-9
- Luna for Health: https://apps.apple.com/us/app/luna-for-health/id1585942826
- Bearable: https://bearable.app/ · https://bearable.app/symptom-tracker/ · https://bearable.app/chronic-illness-symptom-tracker-app/
- Clue: https://helloclue.com/ · https://helloclue.com/articles/about-clue/discover-cycle-phase-insights-understand-your-body-feel-empowered · https://support.helloclue.com/hc/en-us/articles/15007319214493
- Systematic review of good-quality endo mHealth apps (incl. QENDO, Bearable, Luna, Matilda, Branch, CHARLI): https://pmc.ncbi.nlm.nih.gov/articles/PMC11845897/ · https://www.sciencedirect.com/science/article/pii/S2468227623002892
- Symptom tracking in endometriosis (review): https://pmc.ncbi.nlm.nih.gov/articles/PMC10518625/
- Endo symptom clinical references: https://internationalendo.com/endometriosis-symptoms-signs/ · MSD Manual https://www.msdmanuals.com/professional/gynecology-and-obstetrics/endometriosis/endometriosis · Endo belly review https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10671958/ · Endo phenotypes https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12705834/
- Doctor-report apps: Endolog https://www.endolog.app/endometriosis-tracker-app · CareClinic https://careclinic.io/endometriosis-symptom-tracker/ · EndoTracker https://apps.apple.com/us/app/endometriosis-adenomyosis/id6759827829 · Chronius https://www.chroniushealth.com/endometriosis-multidimensional-symptom-tracker
- HealthKit identifiers: https://developer.apple.com/documentation/healthkit/symptom-type-identifiers · https://developer.apple.com/documentation/healthkit/hkcategorytypeidentifier · menstrualFlow/irregularMenstrualCycles/sexualActivity sub-pages
- Privacy / data-monetization context (anti-features): https://www.cam.ac.uk/research/news/menstrual-tracking-app-data-is-a-gold-mine-for-advertisers-that · Flo FTC & post-Dobbs: https://stateline.org/2024/07/26/data-privacy-after-dobbs-is-period-tracking-safe/ · https://pmc.ncbi.nlm.nih.gov/articles/PMC12131320/
- Inclusive design: https://helloclue.com/articles/cycle-a-z/tips-for-using-clue-when-you're-trans · https://redmoongang.com/2022/05/13/how-inclusive-are-period-tracking-apps/

---
*Feature research for: free privacy-first iOS endometriosis tracking & insights app*
*Researched: 2026-06-03*
