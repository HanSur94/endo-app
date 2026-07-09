// EndoInsights (:insights) — the `.correlation` producer (the statistically-intricate honesty engine).
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift` (Phase 7 / INST-03 /
// D-04..D-18 / SC1). Pure-JVM (no android./androidx.; `java.time.LocalDate` + `java.util.Locale` only —
// BoundaryLint is the proof). Mirrors `CyclePhaseMapper`'s shape EXACTLY: an `object` namespace, one
// `insights(from, asOf)` entry, `Equatable` `List<Insight>` output — the SAME snapshots + the SAME injected
// `today` → an equal `List<Insight>`. Per Pattern 2 the injected Swift `Calendar` is DROPPED (LocalDate is
// zone-free day identity); `startOfDay` is a no-op.
//
// THE DELIVERABLE IS THE SUPPRESSION (D-18). The engine emits a `.correlation` Insight ONLY for the top-5
// survivors that clear the three-AND hard gate (n ≥ minSample & each group ≥ 3, |δ|/|RD| ≥ the medium
// floor, AND pass ONE BH-FDR pass at q=0.10 over ALL tested pairs). An under-sampled / spurious /
// multiple-comparison-flood pair never clears the gate → it is NOT emitted (zero false positives). The
// cycle-phase factor derives ONLY from `CyclePhaseMapper`'s windows, so a suppressed/irregular stream
// yields no phase windows → no `factor.cyclePhase.*` pair is ever tested (D-04 BY CONSTRUCTION).
//
// SINGLE SOURCE OF TRUTH: the per-pair sample floor reads [correlationMinSample] — the ONE correlation
// floor (mirrors `HonestyGate.minSample(for: .correlation) == 10`, which lands in 17-08). No second magic
// number. This engine does NOT touch `HonestyGate` / `InsightsEngine`.
package com.endoapp.insights

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** Pure `.correlation` producer. No store, no UI, no I/O, no logging — value-in / value-out. */
object CorrelationEngine {

    // MARK: - Tuning constants (named — no buried literals; 07-RESEARCH A4/A6/§6).

    /** Benjamini–Hochberg false-discovery-rate target, over the FULL candidate set (D-06, A4). */
    const val fdrQ: Double = 0.10

    /** The top-N strongest gate-eligible survivors emitted (D-10, A6) — caps the flood at the source. */
    const val topN: Int = 5

    /** The per-pair sample floor (Phase 7 D-14/A1). WIRED (17-08) to `HonestyGate.minSample(CORRELATION)`
     *  — the ONE source of truth now that the gate exists. This producer self-gates at the SAME floor the
     *  gate enforces, so the gate's min-sample backstop never count-drops a valid correlation survivor. */
    private val correlationMinSample: Int = HonestyGate.minSample(InsightKind.CORRELATION)

    // Cliff's-δ effect-magnitude floors (severity arm) — the locked Romano bands (07-RESEARCH §2A).
    private const val severityMediumFloor: Double = 0.33
    private const val severityLargeFloor: Double = 0.474

    // Risk-difference effect-magnitude floors (presence arm) — the locked RD bands (07-RESEARCH §2B).
    private const val presenceMediumFloor: Double = 0.20
    private const val presenceLargeFloor: Double = 0.35

    // MARK: - The single public entry point (mirrors CyclePhaseMapper.insights EXACTLY).

    /** The `.correlation` `List<Insight>` for these snapshots as of the injected [asOf] day (never read from
     *  the clock inside → deterministic). Maps the shared survivor pipeline to public insights. */
    fun insights(from: List<DaySnapshot>, asOf: LocalDate): List<Insight> =
        scoredSurvivors(from = from, asOf = asOf).map { it.makeInsight() }

    /** PACKAGE-INTERNAL typed accessor for `RecommendationsEngine` (Phase 11 / plan 07). Returns EXACTLY the
     *  same survivors [insights] emits, in the SAME deterministic order, but as typed [CorrelationFinding]s
     *  carrying a `direction` recovered from the SIGNED effect (`meanDiff`/`riskRatio`) — NEVER by parsing the
     *  audited public `.correlation` copy. No public engine egress is added. */
    internal fun scoredCandidates(from: List<DaySnapshot>, asOf: LocalDate): List<CorrelationFinding> =
        scoredSurvivors(from = from, asOf = asOf).map { it.makeFinding() }

    /** The SINGLE shared survivor pipeline (steps 0–6) feeding BOTH [insights] and [scoredCandidates]. Returns
     *  the sorted, gate-filtered (`confidence >= LOW`), top-N survivors. The public `.correlation` behavior
     *  stays byte-identical whichever entry point calls it. */
    private fun scoredSurvivors(from: List<DaySnapshot>, asOf: LocalDate): List<ScoredCandidate> {
        // (0) DEFEND THE PACKAGE BOUNDARY (CR-01/WR-04): a caller that supplies two snapshots with the same
        //     `day` (un-normalized dates, a repository mapping bug, a fixture) must NOT crash the pipeline.
        //     Collapse to ONE snapshot per `day` deterministically — keep the LAST by input order
        //     (`associateBy` is last-wins on a key collision, matching `cyclePhaseLabels`' last-write-wins
        //     idiom so both maps agree on duplicates) — then sort by day so the whole engine sees a clean,
        //     de-duplicated, ordered stream.
        val snapshots = from.associateBy { it.day }.values.sortedBy { it.day }

        // (1) Label every day with its estimated CyclePhase via CyclePhaseMapper (reused VERBATIM, D-04/D-17).
        //     A suppressed/irregular/under-sampled stream → [] windows → every label is null → no phase factor
        //     is ever present → no cyclePhase pair is tested (D-04 by construction, no extra code).
        val phaseByDay = cyclePhaseLabels(snapshots, asOf = asOf)

        // (2) Enumerate the bounded (factor × symptomTarget) pairs (D-09); build a candidate per pair that
        //     clears the sample floor + per-group minimum. Defer FDR + confidence (need ALL p-values first).
        val factors = enumerateFactors(phaseByDay = phaseByDay)
        val candidates = mutableListOf<Candidate>()

        for (factor in factors) {
            // The per-day observed state for this factor (UNOBSERVED = excluded from both groups). `snapshots`
            // is already de-duplicated (step 0), so no collision can occur here.
            val stateByDay: Map<LocalDate, FactorState> = snapshots.associate { snap ->
                snap.day to Factor.stateOf(factor, snap, phaseByDay[snap.day])
            }

            // Severity arm + presence arm, per symptom; bleeding presence arm (OQ3). Sorted for determinism.
            for (symptom in SymptomType.entries) {
                severityCandidate(factor, symptom, snapshots, stateByDay)?.let { candidates.add(it) }
                presenceCandidate(factor, PresenceTarget.Symptom(symptom), snapshots, stateByDay)?.let { candidates.add(it) }
            }
            presenceCandidate(factor, PresenceTarget.Bleeding, snapshots, stateByDay)?.let { candidates.add(it) }
        }

        if (candidates.isEmpty()) return emptyList()

        // (3) Collect ALL K p-values and run BH-FDR ONCE over the full candidate set (NEVER per-pair).
        val pValues = candidates.map { it.pValue }
        val passingIndices = CorrelationStats.benjaminiHochbergPasses(pValues, q = fdrQ)

        // (4) Map each candidate to its InsightConfidence via the three-AND gate (effect floor, n, FDR).
        val scored = candidates.mapIndexed { idx, candidate ->
            val confidence = CorrelationStats.confidence(
                effectMag = candidate.effectMagnitude,
                n = candidate.pairedDays,
                minN = correlationMinSample, // SSOT: the ONE correlation floor (WR-01).
                mediumFloor = candidate.mediumFloor,
                largeFloor = candidate.largeFloor,
                passedFDR = passingIndices.contains(idx),
            )
            ScoredCandidate(candidate = candidate, confidence = confidence)
        }

        // (5) Deterministic order: confidence desc, effectMag desc, pairedDays desc, signalKey asc.
        val sorted = scored.sortedWith(
            compareByDescending<ScoredCandidate> { it.confidence }
                .thenByDescending { it.candidate.effectMagnitude }
                .thenByDescending { it.candidate.pairedDays }
                .thenBy { it.candidate.signalKey },
        )

        // (6) Emit ONLY the top-5 surviving candidates whose confidence >= LOW. Below-gate (`NONE`) pairs are
        //     NOT emitted at all — so "the engine surfaces zero false positives" is direct and the top-N cap
        //     is over gate-ELIGIBLE survivors only (a withheld `NONE` never occupies a slot).
        return sorted
            .filter { it.confidence >= InsightConfidence.LOW }
            .take(topN)
    }

    // MARK: - (1) Cycle-phase labelling — reuse CyclePhaseMapper's windows VERBATIM (D-04/D-17).

    /** Per-day estimated `CyclePhase?` label. Re-derives runs/starts/gaps/median/spread the SAME way
     *  `CyclePhaseMapper.insights` does, then labels each day via `phase(day, windows)`. Suppressed/irregular/
     *  under-sampled → [] windows → every label is `null` → no phase factor (D-04). A day present in the map
     *  with a `null` value means "outside any window"; a non-null value means "labelled in that phase". */
    private fun cyclePhaseLabels(snapshots: List<DaySnapshot>, asOf: LocalDate): Map<LocalDate, CyclePhase?> {
        val labels = HashMap<LocalDate, CyclePhase?>()
        for (snap in snapshots) labels[snap.day] = null // default: outside any window

        val anySuppressed = snapshots.any { it.suppressed }
        if (anySuppressed) return labels // suppressed → no windows (D-04)

        val bleedingDays = snapshots.filter { it.flow != null }.map { it.day }
        val runs = CyclePhaseMapper.periodRuns(bleedingDays)
        val starts = runs.mapNotNull { it.firstOrNull() }
        val gaps = CyclePhaseMapper.consecutiveGaps(starts)
        val lastStart = starts.lastOrNull()
        val lastRun = runs.lastOrNull()
        if (gaps.size < 2 || lastStart == null || lastRun == null) return labels

        val median = CyclePhaseMapper.medianInt(gaps)
        val spread = CyclePhaseMapper.spread(gaps)
        if (CyclePhaseMapper.phaseConfidence(cycleCount = gaps.size, spread = spread) == null) return labels

        val windows = CyclePhaseMapper.phaseWindows(lastStart = lastStart, lastRun = lastRun, median = median, spread = spread)
        if (windows.isEmpty()) return labels

        for (snap in snapshots) labels[snap.day] = CyclePhaseMapper.phase(snap.day, windows)
        return labels
    }

    // MARK: - (2) Factor enumeration — the bounded factor set (D-09), in a deterministic order.

    /** The bounded factor families (07-RESEARCH §1). The cycle-phase family is included ONLY if at least one
     *  day carries a non-null phase label (suppressed/irregular → none → no phase pairs tested, D-04). */
    private fun enumerateFactors(phaseByDay: Map<LocalDate, CyclePhase?>): List<Factor> {
        val factors = mutableListOf<Factor>()
        factors += FoodTag.entries.map { Factor.Food(it) }
        factors += Factor.ActivityPresent
        factors += ActivityType.entries.map { Factor.Activity(it) }
        factors += Factor.SleepPoor
        factors += Factor.StressHigh
        factors += Factor.MoodLow
        factors += Factor.MedicationTaken
        factors += Factor.PrnTaken

        // Cycle-phase factors only when SOME day has a phase label (D-04 by construction).
        val anyPhaseLabel = phaseByDay.values.any { it != null }
        if (anyPhaseLabel) factors += CyclePhase.entries.map { Factor.CyclePhaseFactor(it) }

        // Deterministic key order (Pitfall 4) — sort the whole set by stable key before testing.
        return factors.sortedBy { it.key }
    }

    // MARK: - (3) Per-pair candidate construction (severity arm + presence arm).

    /** What a presence-arm factor is correlated against (D-09: each symptom, plus bleeding per OQ3). */
    private sealed interface PresenceTarget {
        val keyFragment: String

        data class Symptom(val type: SymptomType) : PresenceTarget {
            override val keyFragment: String get() = "symptom.${type.raw}"
        }

        data object Bleeding : PresenceTarget {
            override val keyFragment: String get() = "bleeding"
        }
    }

    /** Severity arm: Cliff's δ + mean-diff + Mann–Whitney p over the two groups of non-null severities for
     *  `symptom`, split by the factor's observed state. Null severities are EXCLUDED (absence ≠ 0). */
    private fun severityCandidate(
        factor: Factor,
        symptom: SymptomType,
        snapshots: List<DaySnapshot>,
        stateByDay: Map<LocalDate, FactorState>,
    ): Candidate? {
        val present = mutableListOf<Double>()
        val absent = mutableListOf<Double>()
        for (snap in snapshots) {
            val state = stateByDay[snap.day] ?: continue
            if (state == FactorState.UNOBSERVED) continue
            // ONE OBSERVATION PER DAY (WR-02): a day may carry more than one SymptomPoint of the same type;
            // appending each would inflate `n`/`sampleSize` past the observed-day count and violate Mann–
            // Whitney's independence assumption. Reduce the day to ONE value (the max recorded severity) so
            // `n` counts DAYS. Null severities are excluded (absence ≠ 0).
            val daySeverities = snap.symptoms.filter { it.type == symptom }.mapNotNull { it.severity }
            val s = daySeverities.maxOrNull() ?: continue
            if (state == FactorState.PRESENT) present.add(s.toDouble()) else absent.add(s.toDouble())
        }
        val pairedDays = present.size + absent.size
        if (!passesSampleFloor(pairedDays = pairedDays, present = present.size, absent = absent.size)) return null
        val delta = CorrelationStats.cliffsDelta(present = present, absent = absent) ?: return null
        val meanDiff = CorrelationStats.meanDifference(present = present, absent = absent) ?: return null
        val p = CorrelationStats.mannWhitneyP(present = present, absent = absent)

        return Candidate(
            signalKey = "correlation.${factor.key}.symptom.${symptom.raw}",
            factorLabel = factor.displayLabel,
            targetLabel = CyclePhaseMapper.displayName(symptom),
            arm = Arm.Severity(meanDiff = meanDiff),
            effectMagnitude = abs(delta),
            pairedDays = pairedDays,
            pValue = p,
            mediumFloor = severityMediumFloor,
            largeFloor = severityLargeFloor,
        )
    }

    /** Presence arm: a 2×2 over paired/observed days → RD + RR + Fisher p. The target is "logged that day". */
    private fun presenceCandidate(
        factor: Factor,
        target: PresenceTarget,
        snapshots: List<DaySnapshot>,
        stateByDay: Map<LocalDate, FactorState>,
    ): Candidate? {
        var a = 0
        var b = 0
        var c = 0
        var d = 0 // a: factor+ & target+, b: factor+ & target-, c: factor- & target+, d: factor- & target-
        for (snap in snapshots) {
            val state = stateByDay[snap.day] ?: continue
            if (state == FactorState.UNOBSERVED) continue
            val targetPresent = when (target) {
                is PresenceTarget.Symptom -> snap.symptoms.any { it.type == target.type }
                is PresenceTarget.Bleeding -> snap.flow != null
            }
            when {
                state == FactorState.PRESENT && targetPresent -> a += 1
                state == FactorState.PRESENT && !targetPresent -> b += 1
                state == FactorState.ABSENT && targetPresent -> c += 1
                state == FactorState.ABSENT && !targetPresent -> d += 1
            }
        }
        val pairedDays = a + b + c + d
        if (!passesSampleFloor(pairedDays = pairedDays, present = a + b, absent = c + d)) return null
        val rd = CorrelationStats.riskDifference(a = a, b = b, c = c, d = d) ?: return null
        val rr = CorrelationStats.riskRatio(a = a, b = b, c = c, d = d)
        val p = CorrelationStats.fisherExactTwoTailed(a = a, b = b, c = c, d = d)

        val targetLabel = when (target) {
            is PresenceTarget.Symptom -> CyclePhaseMapper.displayName(target.type)
            is PresenceTarget.Bleeding -> "bleeding"
        }

        return Candidate(
            signalKey = "correlation.${factor.key}.${target.keyFragment}",
            factorLabel = factor.displayLabel,
            targetLabel = targetLabel,
            arm = Arm.Presence(riskRatio = rr),
            effectMagnitude = abs(rd),
            pairedDays = pairedDays,
            pValue = p,
            mediumFloor = presenceMediumFloor,
            largeFloor = presenceLargeFloor,
        )
    }

    /** Pair-level floor (single source of truth) + per-group minimum. A pair that clears BOTH is tested;
     *  otherwise it is dropped BEFORE emitting (self-gating — it never reaches the FDR/confidence step). */
    private fun passesSampleFloor(pairedDays: Int, present: Int, absent: Int): Boolean =
        pairedDays >= correlationMinSample &&
            present >= Factor.perGroupMinimum &&
            absent >= Factor.perGroupMinimum

    // MARK: - Phase 11 direction recovery (INST-05, D-03; RESEARCH Pattern 2 "Direction semantics").

    /** PACKAGE-INTERNAL typed association direction for a surviving correlation candidate, recovered from the
     *  SIGNED effect (RESEARCH Pattern 2 Option A). `RecommendationsEngine` (plan 07) re-frames this typed
     *  value into direction-aware copy — it is NEVER inferred from the audited public correlation copy. */
    enum class RecommendationDirection {
        /** The factor's present-group fares BETTER (lower symptom severity, or the symptom logged LESS often). */
        BENEFICIAL,

        /** The factor's present-group fares WORSE (higher symptom severity, or the symptom logged MORE often). */
        AGGRAVATING,
    }

    /** PACKAGE-INTERNAL typed survivor the recommendation engine consumes (RESEARCH Pattern 2 Option A).
     *  Carries the same identity/evidence fields the emitted `.correlation` Insight does, PLUS the typed
     *  `direction`. A [CorrelationFinding] exists iff the matching `.correlation` Insight exists (same shared
     *  `scoredSurvivors` pipeline), with identical `signalKey`/`confidence`/`pairedDays`. */
    data class CorrelationFinding(
        val signalKey: String,
        val factorLabel: String,
        val targetLabel: String,
        val confidence: InsightConfidence,
        val pairedDays: Int,
        val effectMagnitude: Double,
        val direction: RecommendationDirection,
    )

    // MARK: - Candidate value types.

    /** Which arm produced a candidate (carries the human-readable effect for the copy). */
    private sealed interface Arm {
        data class Severity(val meanDiff: Double) : Arm
        data class Presence(val riskRatio: Double?) : Arm

        /** The typed association direction (RESEARCH Pattern 2 "Direction semantics"):
         *  - SEVERITY arm: `BENEFICIAL` when `meanDiff < 0` (present-group has LOWER severity), `AGGRAVATING`
         *    when higher. A `0`/non-finite diff defaults to `AGGRAVATING` (the conservative read).
         *  - PRESENCE arm: `AGGRAVATING` when `riskRatio > 1` (target logged MORE often on factor-present days);
         *    `BENEFICIAL` when `< 1`. A `null`/non-finite ratio defaults to `AGGRAVATING` (conservative). */
        val direction: RecommendationDirection
            get() = when (this) {
                is Severity -> if (meanDiff.isFinite() && meanDiff < 0) RecommendationDirection.BENEFICIAL else RecommendationDirection.AGGRAVATING
                is Presence -> {
                    val rr = riskRatio
                    if (rr == null || !rr.isFinite()) RecommendationDirection.AGGRAVATING
                    else if (rr < 1) RecommendationDirection.BENEFICIAL else RecommendationDirection.AGGRAVATING
                }
            }
    }

    /** A tested pair that cleared the sample floor, awaiting FDR + confidence. `effectMagnitude` is |δ|
     *  (severity) or |RD| (presence); `pValue` feeds the single BH-FDR pass. */
    private data class Candidate(
        val signalKey: String,
        val factorLabel: String,
        val targetLabel: String,
        val arm: Arm,
        val effectMagnitude: Double,
        val pairedDays: Int,
        val pValue: Double,
        val mediumFloor: Double,
        val largeFloor: Double,
    )

    /** A candidate with its computed `InsightConfidence` (`NONE` ⇒ withheld, never emitted). */
    private data class ScoredCandidate(val candidate: Candidate, val confidence: InsightConfidence) {

        /** Build the emitted `.correlation` Insight with hand-written NON-CAUSAL copy (D-12/Pitfall 3): ONLY
         *  "possibly associated with" / "logged … often"; NEVER trigger/cause/will/because/linked to/worsen/
         *  improve/reduce (mirrors CyclePhaseMapper's non-causal copy). */
        fun makeInsight(): Insight {
            val factorLabel = candidate.factorLabel
            val targetLabel = candidate.targetLabel
            val headline: String
            val detail: String

            when (val arm = candidate.arm) {
                is Arm.Severity -> {
                    val points = abs(arm.meanDiff).roundedAwayFromZero()
                    val pointWord = if (points == 1.0) "point" else "points"
                    headline = "Higher ${targetLabel.lowercase()} severity possibly associated with logging $factorLabel"
                    detail = "On the days you logged $factorLabel, ${targetLabel.lowercase()} severity averaged " +
                        "~${points.toInt()} $pointWord higher than on other observed days — a descriptive co-occurrence only."
                }

                is Arm.Presence -> {
                    val rr = arm.riskRatio
                    val liftPhrase: String = if (rr != null && rr.isFinite() && rr > 0) {
                        val times = (rr * 10).roundedAwayFromZero() / 10
                        "about ${formatLift(times)}× as often"
                    } else {
                        "more often"
                    }
                    headline = "${targetLabel.capitalizedLikeSwift()} possibly associated with logging $factorLabel"
                    detail = "You logged ${targetLabel.lowercase()} $liftPhrase on the days you logged " +
                        "$factorLabel, across ${candidate.pairedDays} observed days — a descriptive co-occurrence only."
                }
            }

            return Insight.derived(
                kind = InsightKind.CORRELATION,
                window = TrendWindow.ALL_TIME,
                signalKey = candidate.signalKey,
                headline = headline,
                detail = detail,
                confidence = confidence,
                points = emptyList(),
                sampleSize = candidate.pairedDays,
            )
        }

        /** Phase 11 (INST-05, D-01): map this survivor to the typed [CorrelationFinding] the recommendation
         *  engine consumes. The `direction` is recovered from the candidate's `arm` (the SIGNED effect), NOT
         *  from the emitted copy. Identity/evidence fields are the SAME values [makeInsight] emits. */
        fun makeFinding(): CorrelationFinding = CorrelationFinding(
            signalKey = candidate.signalKey,
            factorLabel = candidate.factorLabel,
            targetLabel = candidate.targetLabel,
            confidence = confidence,
            pairedDays = candidate.pairedDays,
            effectMagnitude = candidate.effectMagnitude,
            direction = candidate.arm.direction,
        )

        private fun formatLift(value: Double): String =
            if (value == value.roundedAwayFromZero()) value.toInt().toString()
            else String.format(Locale.ROOT, "%.1f", value)
    }
}

/** Mirror of Foundation `String.capitalized`: title-case the first letter after any non-letter, lowercase
 *  the rest. ASCII-faithful for the label vocabulary (symptom display names + "bleeding"). */
private fun String.capitalizedLikeSwift(): String {
    val sb = StringBuilder(length)
    var atWordStart = true
    for (ch in this) {
        if (ch.isLetter()) {
            sb.append(if (atWordStart) ch.uppercaseChar() else ch.lowercaseChar())
            atWordStart = false
        } else {
            sb.append(ch)
            atWordStart = true
        }
    }
    return sb.toString()
}
