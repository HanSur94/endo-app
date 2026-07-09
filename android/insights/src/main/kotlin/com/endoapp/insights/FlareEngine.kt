// EndoInsights (:insights) — the flare early-warning producer.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/FlareEngine.swift` (Phase 12, Plan 02, INST-06 /
// D-01..D-08 / SC1 / V7). THE hardest, riskiest capability — conservative, biased toward SILENCE. A
// heuristic, ML-free, non-diagnostic flare early-warning derived from the user's OWN history: it COMPOSES
// three already-gated signals (a recent rising-severity spine, currently-active known triggers, proximity to
// a historically higher-symptom cycle phase) into a BOUNDED (<= MEDIUM), uncertainty-first confidence — and
// WITHHOLDS entirely when the evidence is insufficient. Pure-JVM (BoundaryLint is the proof). Per Pattern 2
// the injected Swift `Calendar` is DROPPED (LocalDate is day-identity; `startOfDay` is a no-op).
//
// COMPOSE, NEVER RE-DERIVE (D-01/D-02): the engine consumes `TrendsEngine` (rising severity),
// `CorrelationEngine.scoredCandidates` (active triggers), and `CyclePhaseMapper.phaseWindows` (cycle-phase
// proximity) — it runs NO raw statistics of its own. The rising-severity spine is REQUIRED: no recent rise →
// NO warning (silence is honest). Active triggers and cycle-phase proximity can only RAISE the confidence one
// notch each on top of an existing rising spine — they can NEVER start a warning alone.
//
// BIAS TO WITHHOLD (D-03/D-05): a suppressed / flat / insufficient user yields ZERO flare warning (or, on the
// A3 cusp — a real recent rise but sub-21-day evidence — ONE honest "not enough data yet" notice). The
// confidence is CAPPED at MEDIUM (NEVER HIGH), floored at LOW; the warning is a words-only card. A flare
// warning is never alarmist/diagnostic/certain.
//
// SINGLE SOURCE OF TRUTH: the evidence floor reads [flareMinSample] — mirrors `HonestyGate.minSample(for:
// .flare) == 21`, which lands in 17-08. Until then this named constant is the single source of the flare
// floor (exactly one literal, to be replaced by the `HonestyGate` call once it is wired).
//
// STABLE SIGNALKEY (D-07): the warning's signalKey is the value/window-INDEPENDENT "flare.warning" with the
// `.allTime` window. The insufficiency notice shares the SAME signalKey (one flare signal); the typed
// `isInsufficiencyNotice` marker (NOT a signalKey suffix — WR-01) carries it past the gate floor.
//
// APP↔PACKAGE COPY MIRROR: the package CANNOT import the app (SC1), so the curated, calm, uncertainty-first
// copy lives HERE in a package-internal `FlareCopyText`, BYTE-IDENTICAL to the app's audited `FlareCopy`.
// `FlareDriftGuard` (public) forwards those strings for the Phase-18 byte-identity test. (The
// `ForbiddenTerms.alarmist` forwarder the Swift shim also exposes is deferred to 17-08.)
package com.endoapp.insights

import java.time.LocalDate

/** Pure `.flare` early-warning producer. No store, no UI, no I/O, no logging — value-in / value-out. */
object FlareEngine {

    /** The evidence floor — mirrors `HonestyGate.minSample(for: .flare) == 21`. `HonestyGate` lands in
     *  17-08; until then this named constant is the SINGLE source of the flare floor (Pitfall 4 — so the
     *  gate's backstop never re-drops a valid warning). */
    private const val flareMinSample: Int = 21

    // MARK: - The single public entry point (mirrors RecommendationsEngine.recommendations, minus Calendar).

    /** Compose the three already-gated signals into AT MOST one bounded (<= MEDIUM), uncertainty-first
     *  `.flare` Insight — or, on the A3 cusp, one honest "not enough data yet" notice — biased toward
     *  WITHHOLDING. NO re-derived statistics (D-01); the rising-severity spine is REQUIRED. */
    fun flareWarning(from: List<DaySnapshot>, asOf: LocalDate): List<Insight> {
        // 1. SUPPRESSED SHORT-CIRCUIT FIRST (D-08/SC3): a user who has turned insight derivation OFF gets ZERO
        //    flare warning — the most caution-sensitive surface is the MOST conservative. Never a phantom warning.
        if (from.any { it.suppressed }) return emptyList()

        val today0 = asOf // LocalDate is already day-identity — startOfDay is a no-op.

        // 2. SIGNAL A — the rising-severity spine (REQUIRED). Compose TrendsEngine helpers (never re-derive).
        //    No rising symptom → return [] (silence is honest, NOT an insufficiency notice).
        val rising = risingSeveritySignal(from, today0 = today0) ?: return emptyList()

        // 5. EVIDENCE FLOOR (D-05 / A3). The evidence is the rising signal's logged-severity day count over the
        //    recent window, gated against the SAME `flareMinSample == 21` the gate enforces. Below the floor: a
        //    real recent rise but insufficient evidence → ONE honest "not enough data yet" notice, never a warning.
        val evidence = rising.loggedDays
        if (evidence < flareMinSample) return listOf(insufficiencyNotice(evidence = evidence))

        // 3. SIGNAL B — currently-active aggravating triggers (count only). Reuse CorrelationEngine.scoredCandidates.
        val activeTriggerCount = activeAggravatingTriggers(from, today0 = today0)

        // 4. SIGNAL C — cycle-phase proximity (boolean). Reuse CyclePhaseMapper.phaseWindows.
        val nearHigherPhase = cyclePhaseProximity(rising = rising, snapshots = from, today0 = today0)

        // 6. BOUNDED COMBINE (D-03): start at LOW (the rising spine exists), +1 notch per secondary signal, CAP
        //    at MEDIUM (NEVER HIGH), floor LOW (bias to withhold). Emit EXACTLY one .flare Insight.
        val confidence = boundedConfidence(activeTriggerCount = activeTriggerCount, nearHigherPhase = nearHigherPhase)
        if (confidence < InsightConfidence.LOW) return emptyList() // bias to withhold (defensive; LOW is the floor).

        val note = contributingNote(activeTriggerCount = activeTriggerCount, nearHigherPhase = nearHigherPhase)
        return listOf(makeFlareInsight(confidence = confidence, evidence = evidence, contributing = note))
    }

    // MARK: - Signal A: the REQUIRED rising-severity spine (compose TrendsEngine, never re-derive).

    /** The dominant logged symptom whose RECENT severity is rising, with the evidence day-count it is based
     *  on. */
    private data class RisingSignal(val symptom: SymptomType, val loggedDays: Int)

    private fun risingSeveritySignal(snapshots: List<DaySnapshot>, today0: LocalDate): RisingSignal? {
        // The recent vs immediately-prior short windows (TrendsEngine's bucket math, composed verbatim).
        val recent7 = TrendsEngine.bucket(snapshots, window = TrendWindow.LAST7, endingAt = today0)
        val prior7 = TrendsEngine.priorBucket(snapshots, window = TrendWindow.LAST7, endingAt = today0) ?: return null
        // The wider recent window the evidence count is drawn from (substantial-history floor, D-02).
        val recent30 = TrendsEngine.bucket(snapshots, window = TrendWindow.LAST30, endingAt = today0)

        // Dominant symptom: most logged-severity days in the recent short bucket; tie → lowest rawValue.
        val typesLogged = recent7.flatMap { snap ->
            snap.symptoms.filter { it.severity != null }.map { it.type }
        }.toSet()
        if (typesLogged.isEmpty()) return null

        fun loggedSeverityDays(bucket: List<DaySnapshot>, type: SymptomType): Int =
            bucket.count { snap -> snap.symptoms.any { it.type == type && it.severity != null } }

        val dominant = typesLogged.sortedWith(
            compareByDescending<SymptomType> { loggedSeverityDays(recent7, it) }.thenBy { it.raw },
        ).first()

        // Rising iff the recent mean is meaningfully HIGHER than the prior mean (TrendsEngine.direction).
        val recentMean = TrendsEngine.meanSeverity(recent7, dominant) ?: return null
        val priorMean = TrendsEngine.meanSeverity(prior7, dominant)
        val dir = TrendsEngine.direction(recentMean = recentMean, priorMean = priorMean, window = TrendWindow.LAST7)
        if (dir != TrendsEngine.Direction.UP) return null

        return RisingSignal(symptom = dominant, loggedDays = loggedSeverityDays(recent30, dominant))
    }

    // MARK: - Signal B: currently-active aggravating triggers (reuse CorrelationEngine, never re-derive).

    /** The number of AGGRAVATING correlation survivors whose factor the user logged on at least one recent
     *  (`.last30`) day. Reuses `CorrelationEngine.scoredCandidates` — runs NO correlation stats of its own. */
    private fun activeAggravatingTriggers(snapshots: List<DaySnapshot>, today0: LocalDate): Int {
        val findings = CorrelationEngine.scoredCandidates(from = snapshots, asOf = today0)
        if (findings.isEmpty()) return 0

        val recent30 = TrendsEngine.bucket(snapshots, window = TrendWindow.LAST30, endingAt = today0)
        // The set of factor keys the user logged as PRESENT on at least one recent day.
        val recentlyActiveFactorKeys = activeFactorKeys(recent30)

        var count = 0
        for (finding in findings) {
            if (finding.direction != CorrelationEngine.RecommendationDirection.AGGRAVATING) continue
            val factorKey = factorKey(finding.signalKey) ?: continue
            if (recentlyActiveFactorKeys.contains(factorKey)) count += 1
        }
        return count
    }

    /** The set of `Factor.key`s that are PRESENT on at least one day in `bucket` (the recently-active factors).
     *
     *  WR-02 — INTENTIONAL ASYMMETRY (do NOT "fix" by adding cyclePhase keys): this set DELIBERATELY omits the
     *  `cyclePhase.<phase>` factor family. Cycle-phase contribution is ALREADY the job of Signal C; emitting
     *  cyclePhase keys HERE too would let one cycle-phase signal raise confidence via BOTH Signal B and Signal
     *  C — double-counting the same evidence. Keep the omission. */
    private fun activeFactorKeys(bucket: List<DaySnapshot>): Set<String> {
        val keys = HashSet<String>()
        for (snap in bucket) {
            for (tag in snap.foodTags) keys.add(Factor.Food(tag).key)
            val type = snap.activityType
            if (type != null) {
                keys.add(Factor.ActivityPresent.key)
                keys.add(Factor.Activity(type).key)
            }
            if (snap.sleepQuality == SleepQuality.POOR) keys.add(Factor.SleepPoor.key)
            val stress = snap.stress
            if (stress != null && stress >= Factor.highStressThreshold) keys.add(Factor.StressHigh.key)
            val mood = snap.mood
            if (mood != null && mood <= Factor.lowMoodThreshold) keys.add(Factor.MoodLow.key)
            if (snap.medicationsTaken.isNotEmpty()) keys.add(Factor.MedicationTaken.key)
            if (snap.tookPRN) keys.add(Factor.PrnTaken.key)
        }
        return keys
    }

    /** The two-component `<family>.<value>` factor key from a `correlation.<factor.key>.<target…>` signalKey
     *  (`food.dairy`, `sleep.poor`, `cyclePhase.luteal`, …), or `null` if the shape is unexpected. */
    private fun factorKey(correlationSignalKey: String): String? {
        val parts = correlationSignalKey.split(".")
        if (parts.size < 3 || parts[0] != "correlation") return null
        return "${parts[1]}.${parts[2]}"
    }

    // MARK: - Signal C: cycle-phase proximity (reuse CyclePhaseMapper, never re-derive).

    /** `true` iff `today` falls in an estimated phase window AND that phase is the one with the MOST logged
     *  dominant-symptom days historically (a "historically higher-symptom phase"). Reuses
     *  `CyclePhaseMapper.phaseWindows` — inherits []-on-suppressed/irregular/<2-cycle (no windows → false). */
    private fun cyclePhaseProximity(rising: RisingSignal, snapshots: List<DaySnapshot>, today0: LocalDate): Boolean {
        val windows = estimatedPhaseWindows(snapshots, today0 = today0)
        if (windows.isEmpty()) return false

        // Which phase is today in? (null → not near any phase window.)
        val todayPhase = CyclePhaseMapper.phase(today0, windows) ?: return false

        // The historically-highest dominant-symptom phase: count logged dominant-symptom days per phase across
        // all history. today's phase must BE that phase to count as "near a higher-symptom phase".
        val perPhaseSymptomDays = HashMap<CyclePhase, Int>()
        for (snap in snapshots) {
            val phase = CyclePhaseMapper.phase(snap.day, windows) ?: continue
            // WR-03 — count a day toward "historically higher-symptom phase" only when the dominant symptom was
            // logged WITH A SEVERITY (`severity != null`), the SAME logged-severity basis the rising spine and
            // the 21-day evidence floor use. Counting bare presence here would make this corroborating signal
            // MORE permissive than the spine it strengthens — the opposite of the bias-to-withhold posture.
            if (snap.symptoms.any { it.type == rising.symptom && it.severity != null }) {
                perPhaseSymptomDays[phase] = (perPhaseSymptomDays[phase] ?: 0) + 1
            }
        }
        // Deterministic argmax: most days wins; tie → lowest rawValue.
        val highest = perPhaseSymptomDays.entries
            .sortedWith(compareByDescending<Map.Entry<CyclePhase, Int>> { it.value }.thenBy { it.key.raw })
            .firstOrNull()?.key
        if (highest == null || (perPhaseSymptomDays[highest] ?: 0) <= 0) return false
        return todayPhase == highest
    }

    /** Re-derive the current cycle's estimated phase windows the SAME way `CyclePhaseMapper.insights` does,
     *  reused verbatim (D-04/D-17). Suppressed/irregular/<2-cycle → `[]` (no proximity). */
    private fun estimatedPhaseWindows(snapshots: List<DaySnapshot>, today0: LocalDate): List<CyclePhaseMapper.PhaseWindow> {
        val bleedingDays = snapshots.filter { it.flow != null }.map { it.day }
        val runs = CyclePhaseMapper.periodRuns(bleedingDays)
        val starts = runs.mapNotNull { it.firstOrNull() }
        val gaps = CyclePhaseMapper.consecutiveGaps(starts)
        val lastStart = starts.lastOrNull()
        val lastRun = runs.lastOrNull()
        if (gaps.size < 2 || lastStart == null || lastRun == null) return emptyList()
        val median = CyclePhaseMapper.medianInt(gaps)
        val spread = CyclePhaseMapper.spread(gaps)
        if (CyclePhaseMapper.phaseConfidence(cycleCount = gaps.size, spread = spread) == null) return emptyList()
        return CyclePhaseMapper.phaseWindows(lastStart = lastStart, lastRun = lastRun, median = median, spread = spread)
    }

    // MARK: - Bounded combine (D-03 — start LOW, +1 notch each, CAP MEDIUM, NEVER HIGH).

    /** Start at `LOW` (the rising spine exists); `+1 notch` if any aggravating trigger is currently active;
     *  `+1 notch` if today is near a historically higher-symptom phase; CAP at `MEDIUM`. NEVER `HIGH` — a
     *  flare warning is uncertainty-first even at its strongest. */
    private fun boundedConfidence(activeTriggerCount: Int, nearHigherPhase: Boolean): InsightConfidence {
        var notches = 1 // LOW — the rising spine is present.
        if (activeTriggerCount >= 1) notches += 1
        if (nearHigherPhase) notches += 1
        val capped = minOf(notches, InsightConfidence.MEDIUM.value) // cap at MEDIUM (value 2), never HIGH.
        return InsightConfidence.entries.firstOrNull { it.value == capped } ?: InsightConfidence.LOW
    }

    // MARK: - The "what's contributing" note (from the TYPED signals, NEVER parsed copy).

    /** The curated `FlareCopyText.contributing` line — appended ONLY when at least one secondary signal fired.
     *  Derived from the TYPED signals, never parsed from the audited copy. `null` when only the bare rising
     *  spine exists. */
    private fun contributingNote(activeTriggerCount: Int, nearHigherPhase: Boolean): String? =
        if (activeTriggerCount >= 1 || nearHigherPhase) FlareCopyText.contributing else null

    // MARK: - Insight construction (stable signalKey "flare.warning", window .allTime, words-only).

    /** The one bounded `.flare` warning. Stable window-independent signalKey "flare.warning" (D-07);
     *  `.allTime` window; words-only (`points: []`); `sampleSize == evidence`. */
    private fun makeFlareInsight(confidence: InsightConfidence, evidence: Int, contributing: String?): Insight {
        val detail = if (contributing != null) "${FlareCopyText.detail} $contributing" else FlareCopyText.detail
        return Insight.derived(
            kind = InsightKind.FLARE,
            window = TrendWindow.ALL_TIME,
            signalKey = "flare.warning",
            headline = FlareCopyText.headline,
            detail = detail,
            confidence = confidence,
            points = emptyList(),
            sampleSize = evidence,
        )
    }

    /** The single honest "not enough data yet" notice (A3 cusp: a real recent rise but sub-21 evidence). SHARES
     *  the SAME signalKey "flare.warning" (one flare signal); the typed `isInsufficiencyNotice` marker (NOT a
     *  signalKey suffix — WR-01) carries it past the gate's min-sample + confidence floor. */
    private fun insufficiencyNotice(evidence: Int): Insight =
        Insight.derived(
            kind = InsightKind.FLARE,
            window = TrendWindow.ALL_TIME,
            signalKey = "flare.warning",
            headline = FlareCopyText.insufficientTitle,
            detail = FlareCopyText.insufficientSubtitle,
            confidence = InsightConfidence.NONE,
            points = emptyList(),
            sampleSize = evidence,
            isInsufficiencyNotice = true,
        )
}

// MARK: - Package-internal curated copy mirror (BYTE-IDENTICAL to the app's audited FlareCopy).

/** PACKAGE-LOCAL MIRROR of the app's `FlareCopy`: the package CANNOT import the app target (SC1), so the SAME
 *  curated, calm, uncertainty-first, NON-alarmist strings are duplicated here as data and MUST stay
 *  byte-identical with the app surface.
 *
 *  SUBSTRING TRAP: every word is CURATED to avoid the forbidden substrings ENTIRELY. The two LOAD-BEARING
 *  traps Phase 12 dodges (verified BY HAND against alarmist ∪ causal ∪ imperative ∪ gendered ∪ numeric):
 *    • "more difficult days" — no "her" (avoids the "higher" gendered trap), no "predict", no alarmist word.
 *    • the detail deliberately OMITS "not a prediction" (that would trip "predict") — the not-medical-advice
 *      framing is carried by the pinned DisclaimerBanner + "discuss with your clinician".
 *    • "warning" appears ONLY in `sectionFlare` ("Flare early-warning", D-07) — the one sanctioned use. */
internal object FlareCopyText {

    /** The "Flare early-warning" section title (the ONLY sanctioned use of "warning", D-07). */
    const val sectionFlare: String = "Flare early-warning"

    /** The calm, uncertainty-first headline ("more difficult days", NOT "higher-symptom days"). */
    const val headline: String = "Your recent pattern looks similar to times before more difficult days."

    /** The honest "your own data, keep an eye on it, discuss with your clinician" detail line. */
    const val detail: String =
        "This is a pattern in your own data — something to keep an eye on and discuss with your clinician."

    /** The optional "what's contributing" line, appended ONLY when a secondary signal fired (from the TYPED
     *  engine signals, never parsed copy). */
    const val contributing: String =
        "Based on a recent rising pattern and a factor you have logged recently."

    /** The insufficiency-notice title (reuses the sparse-title wording for parity). */
    const val insufficientTitle: String = "Not enough data yet."

    /** The insufficiency-notice subtitle — the honest "keep logging" framing. */
    const val insufficientSubtitle: String = "Keep logging and this fills in over time."
}

// MARK: - Cross-boundary drift-guard surface — the ONLY public window onto the copy mirror.

/** PUBLIC, TEST-SUPPORT-ONLY window onto the package-internal `FlareCopyText` mirror the APP duplicates and
 *  must keep in sync. The app test target sees ONLY the package's PUBLIC API, so the package-internal
 *  `FlareCopyText` strings are invisible to it. This shim forwards those mirrored strings — and NOTHING else —
 *  so an app-side test (`FlareCopyDriftTests`) can assert byte-identity and a future edit to ONE side without
 *  the other fails a test. The forwarded values are pure data with no behavior; exposing them does NOT widen
 *  the engine's API or the Foundation-only boundary.
 *
 *  NOTE (17-08): the Swift `FlareDriftGuard` also forwards `ForbiddenTerms.alarmist`. That list lands in 17-08
 *  (`ForbiddenTerms.kt`); the forwarder is added there so this file compiles before `ForbiddenTerms` exists. */
object FlareDriftGuard {

    /** The package-internal `FlareCopyText.headline` mirror, for byte-identity comparison against the app's
     *  audited `FlareCopy.headline`. */
    val packageHeadline: String get() = FlareCopyText.headline

    /** The package-internal `FlareCopyText.detail` mirror. */
    val packageDetail: String get() = FlareCopyText.detail

    /** The package-internal `FlareCopyText.contributing` mirror. */
    val packageContributing: String get() = FlareCopyText.contributing

    /** The package-internal `FlareCopyText.sectionFlare` mirror. */
    val packageSectionFlare: String get() = FlareCopyText.sectionFlare

    /** The package-internal `FlareCopyText.insufficientTitle` mirror. */
    val packageInsufficientTitle: String get() = FlareCopyText.insufficientTitle

    /** The package-internal `FlareCopyText.insufficientSubtitle` mirror. */
    val packageInsufficientSubtitle: String get() = FlareCopyText.insufficientSubtitle
}
