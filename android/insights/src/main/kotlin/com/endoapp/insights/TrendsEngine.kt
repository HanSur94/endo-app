// EndoInsights (:insights) — the pure, deterministic descriptive-trends engine.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/TrendsEngine.swift` (INST-01 / SC2). Pure-JVM
// (no android./androidx.; `java.time.LocalDate` only — the BoundaryLint is the proof). Swift's `enum`
// namespace becomes a Kotlin `object`; per Pattern 2 the injected `Calendar` is DROPPED and `today` is a
// `LocalDate`. Same inputs + same injected `today` → an equal `List<Insight>`.
//
// HONESTY (D-03/D-08/D-16, the endo pitfalls):
//   • absence ≠ 0 — a null reading is EXCLUDED from a mean's denominator, never summed as 0.
//   • no phantom cycle — the bleeding trend is GATED on `!suppressed && bleedingDayCount >= 1`
//     (mirrors `CycleMath.summarize`'s `if isSuppressed { return .suppressed }` short-circuit — SC3).
//   • no false precision — below the per-trend `minLoggedDays` threshold the engine NEVER emits a
//     misleading trend (D-08). For symptom FREQUENCY specifically it surfaces an explicit "not enough
//     data yet" Insight; every other sub-threshold path stays HONESTLY SILENT.
//   • descriptive, non-causal copy — no "trigger/causes/cause/will" (D-16).
package com.endoapp.insights

import java.time.LocalDate
import java.util.Locale

/** Pure descriptive-trends engine. No store, no UI, no I/O, no logging — value-in/value-out. */
object TrendsEngine {

    // MARK: - Tunable, unit-asserted constants (no buried magic numbers — D-08).

    /** Minimum logged days for a signal in a window before a confident trend is emitted. Below this the
     *  engine emits an explicit "not enough data" Insight (mirrors `CycleMath`'s "≥ 2 cycles" spirit). */
    const val minLoggedDays: Int = 3

    /** The half-band (in the signal's units) within which a recent-vs-prior delta reads as "steady",
     *  avoiding false precision on tiny movements. */
    const val directionBand: Double = 0.5

    // MARK: - The single public entry point (mirrors CycleMath.summarize).

    /** Descriptive trends over the 7/30/90-day + all-time windows anchored on absolute calendar dates
     *  ending at the injected `today` (NOT cycle phase). */
    fun trends(from: List<DaySnapshot>, asOf: LocalDate): List<Insight> {
        val today0 = asOf // LocalDate is already day-identity — startOfDay is a no-op.
        val out = mutableListOf<Insight>()

        for (window in TrendWindow.entries) {
            val recent = bucket(from, window = window, endingAt = today0)
            // The immediately-prior equal-length window (null for allTime → no direction).
            val prior = priorBucket(from, window = window, endingAt = today0)

            out += symptomTrends(recent = recent, prior = prior, window = window)
            out += factorTrends(recent = recent, prior = prior, window = window)
            out += bleedingTrend(recent = recent, window = window)
        }
        return out
    }

    // MARK: - Window bucketing (DST-safe day math — never epoch arithmetic).

    /** The snapshots whose `day` falls in `[end-(N-1) … end]` inclusive (allTime = everything). */
    fun bucket(snapshots: List<DaySnapshot>, window: TrendWindow, endingAt: LocalDate): List<DaySnapshot> {
        val n = windowLength(window) ?: return snapshots
        val start0 = endingAt.minusDays((n - 1).toLong())
        return snapshots.filter { snap ->
            val d = snap.day
            d >= start0 && d <= endingAt
        }
    }

    /** The immediately-prior equal-length window `[end-(2N-1) … end-N]`; null for allTime. */
    fun priorBucket(snapshots: List<DaySnapshot>, window: TrendWindow, endingAt: LocalDate): List<DaySnapshot>? {
        val n = windowLength(window) ?: return null
        val priorEnd = endingAt.minusDays(n.toLong())
        val priorStart = endingAt.minusDays((2 * n - 1).toLong())
        return snapshots.filter { snap ->
            val d = snap.day
            d >= priorStart && d <= priorEnd
        }
    }

    /** Day-length of a finite window; null for `allTime`. */
    fun windowLength(window: TrendWindow): Int? = when (window) {
        TrendWindow.LAST7 -> 7
        TrendWindow.LAST30 -> 30
        TrendWindow.LAST90 -> 90
        TrendWindow.ALL_TIME -> null
    }

    // MARK: - Per-symptom severity mean + frequency (D-05/D-07).

    fun symptomTrends(recent: List<DaySnapshot>, prior: List<DaySnapshot>?, window: TrendWindow): List<Insight> {
        val typesLogged = recent.flatMap { it.symptoms.map { s -> s.type } }.toSet()
        val out = mutableListOf<Insight>()
        val daysWithAnyLog = recent.size

        for (type in typesLogged.sortedBy { it.raw }) {
            val label = displayName(type)
            val daysPresent = recent.count { snap -> snap.symptoms.any { it.type == type } }

            // --- Frequency trend (days-present / days-with-any-log) ---
            if (daysWithAnyLog >= minLoggedDays) {
                out.add(
                    Insight.derived(
                        kind = InsightKind.TREND,
                        window = window,
                        signalKey = "symptom.${type.raw}.frequency",
                        headline = "$label logged on $daysPresent of $daysWithAnyLog days you tracked anything",
                        detail = "You logged ${label.lowercase()} on $daysPresent of the $daysWithAnyLog days you tracked anything ${windowPhrase(window)}.",
                        confidence = confidence(daysPresent),
                        points = frequencyPoints(recent) { snap -> snap.symptoms.any { it.type == type } },
                        sampleSize = daysWithAnyLog,
                    ),
                )
            } else {
                out.add(
                    notEnoughData(signal = "symptom.${type.raw}.frequency", label = label, window = window, loggedDays = daysWithAnyLog),
                )
            }

            // --- Severity-mean trend (null severities excluded from the denominator) ---
            val severities = recent.mapNotNull { snap ->
                snap.symptoms.firstOrNull { it.type == type }?.severity
            }.map { it.toDouble() }
            if (severities.size >= minLoggedDays) {
                val mean = average(severities)!!
                val dir = direction(
                    recentMean = mean,
                    priorMean = prior?.let { meanSeverity(it, type) },
                    window = window,
                )
                out.add(
                    Insight.derived(
                        kind = InsightKind.TREND,
                        window = window,
                        signalKey = "symptom.${type.raw}.severity",
                        headline = "$label severity averaged ${format(mean)} ${windowPhrase(window)}",
                        detail = "Average logged ${label.lowercase()} severity was ${format(mean)}${directionPhrase(dir, window)}.",
                        confidence = confidence(severities.size),
                        points = severityPoints(recent, type),
                        sampleSize = severities.size,
                    ),
                )
            }
        }
        return out
    }

    // MARK: - Per-factor frequency + stress/mood means (D-05/D-07).

    fun factorTrends(recent: List<DaySnapshot>, prior: List<DaySnapshot>?, window: TrendWindow): List<Insight> {
        val out = mutableListOf<Insight>()

        // Stress mean (null excluded).
        scaleMeanTrend(recent, prior, window, value = { it.stress?.toDouble() }, label = "stress", signal = "factor.stress.mean")
            ?.let { out.add(it) }
        // Mood mean (null excluded).
        scaleMeanTrend(recent, prior, window, value = { it.mood?.toDouble() }, label = "mood", signal = "factor.mood.mean")
            ?.let { out.add(it) }

        // Poor-sleep-day frequency (denominator = days sleep quality was logged).
        val sleepDays = recent.count { it.sleepQuality != null }
        val poorDays = recent.count { it.sleepQuality == SleepQuality.POOR }
        if (sleepDays >= minLoggedDays && poorDays >= 1) {
            out.add(
                Insight.derived(
                    kind = InsightKind.TREND,
                    window = window,
                    signalKey = "factor.sleep.poor.frequency",
                    headline = "Poor sleep on $poorDays of $sleepDays logged sleep days",
                    detail = "You rated sleep poor on $poorDays of the $sleepDays days you logged sleep ${windowPhrase(window)}.",
                    confidence = confidence(sleepDays),
                    points = frequencyPoints(recent.filter { it.sleepQuality != null }) { it.sleepQuality == SleepQuality.POOR },
                    sampleSize = sleepDays,
                ),
            )
        }
        return out
    }

    fun scaleMeanTrend(
        recent: List<DaySnapshot>,
        prior: List<DaySnapshot>?,
        window: TrendWindow,
        value: (DaySnapshot) -> Double?,
        label: String,
        signal: String,
    ): Insight? {
        val readings = recent.mapNotNull(value)
        if (readings.size < minLoggedDays) return null
        val mean = average(readings)!!
        val priorMean = prior?.mapNotNull(value)?.let { if (it.isEmpty()) null else average(it) }
        val dir = direction(recentMean = mean, priorMean = priorMean, window = window)
        return Insight.derived(
            kind = InsightKind.TREND,
            window = window,
            signalKey = signal,
            headline = "${label.replaceFirstChar { it.uppercase() }} averaged ${format(mean)} ${windowPhrase(window)}",
            detail = "Average logged $label was ${format(mean)}${directionPhrase(dir, window)}.",
            confidence = confidence(readings.size),
            points = listOf(InsightPoint(date = lastDay(recent), value = mean)),
            sampleSize = readings.size,
        )
    }

    // MARK: - Bleeding-day frequency, GATED on !suppressed && bleedingDayCount >= 1 (SC3).

    fun bleedingTrend(recent: List<DaySnapshot>, window: TrendWindow): List<Insight> {
        // The suppressed short-circuit, mirroring CycleMath's `if isSuppressed`. If ANY in-window day is
        // suppressed, OR there is no bleeding logged, emit NO bleeding/cycle trend (the no-phantom proof).
        val anySuppressed = recent.any { it.suppressed }
        val bleedingDayCount = recent.count { it.flow != null }
        if (!(!anySuppressed && bleedingDayCount >= 1)) return emptyList()

        val total = recent.size
        return listOf(
            Insight.derived(
                kind = InsightKind.TREND,
                window = window,
                signalKey = "bleeding.frequency",
                headline = "Bleeding logged on $bleedingDayCount of $total days",
                detail = "You logged bleeding on $bleedingDayCount of the $total days you tracked ${windowPhrase(window)}.",
                confidence = confidence(bleedingDayCount),
                points = frequencyPoints(recent) { it.flow != null },
                sampleSize = total,
            ),
        )
    }

    // MARK: - "Not enough data" honesty Insight (D-08).

    fun notEnoughData(signal: String, label: String, window: TrendWindow, loggedDays: Int = 0): Insight =
        Insight.derived(
            kind = InsightKind.TREND,
            window = window,
            signalKey = "$signal.insufficient",
            headline = "$label: not enough data yet",
            detail = "Not enough data yet to describe ${label.lowercase()} ${windowPhrase(window)} — keep logging.",
            confidence = InsightConfidence.NONE,
            points = emptyList(),
            sampleSize = loggedDays,
            isInsufficiencyNotice = true,
        )

    // MARK: - Pure helpers (each independently testable).

    /** Mean of a non-empty set of values; null for an empty set (null is NEVER coerced to 0). */
    fun average(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        return values.sum() / values.size.toDouble()
    }

    fun meanSeverity(snapshots: List<DaySnapshot>, type: SymptomType): Double? =
        average(
            snapshots.mapNotNull { snap -> snap.symptoms.firstOrNull { it.type == type }?.severity }.map { it.toDouble() },
        )

    /** A coarse, non-causal direction: `up`/`down`/`steady` with a band to avoid false precision.
     *  `null` prior (allTime) → `NONE`. */
    enum class Direction { UP, DOWN, STEADY, NONE }

    fun direction(recentMean: Double, priorMean: Double?, window: TrendWindow): Direction {
        if (windowLength(window) == null || priorMean == null) return Direction.NONE
        val delta = recentMean - priorMean
        if (delta > directionBand) return Direction.UP
        if (delta < -directionBand) return Direction.DOWN
        return Direction.STEADY
    }

    /** Descriptive, NON-CAUSAL, present/past-tense phrasing of a direction (no "trigger/cause/will"). */
    fun directionPhrase(dir: Direction, window: TrendWindow): String = when (dir) {
        Direction.UP -> ", higher than the prior period"
        Direction.DOWN -> ", lower than the prior period"
        Direction.STEADY -> ", about the same as the prior period"
        Direction.NONE -> "" // allTime / no prior window → omit direction entirely
    }

    fun confidence(forLoggedDays: Int): InsightConfidence = when {
        forLoggedDays < minLoggedDays -> InsightConfidence.NONE
        forLoggedDays < 7 -> InsightConfidence.LOW
        forLoggedDays < 14 -> InsightConfidence.MEDIUM
        else -> InsightConfidence.HIGH
    }

    fun format(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

    fun windowPhrase(window: TrendWindow): String = when (window) {
        TrendWindow.LAST7 -> "in the last 7 days"
        TrendWindow.LAST30 -> "in the last 30 days"
        TrendWindow.LAST90 -> "in the last 90 days"
        TrendWindow.ALL_TIME -> "across all your logs"
    }

    fun displayName(type: SymptomType): String = when (type) {
        SymptomType.PELVIC_PAIN -> "Pelvic pain"
        SymptomType.DYSMENORRHEA -> "Period pain"
        SymptomType.DYSPAREUNIA -> "Painful sex"
        SymptomType.GI_ENDO_BELLY -> "Endo belly / GI"
        SymptomType.URINARY -> "Urinary symptoms"
        SymptomType.FATIGUE -> "Fatigue"
        SymptomType.BRAIN_FOG -> "Brain fog"
        SymptomType.LOW_MOOD -> "Low mood"
        SymptomType.HEADACHE -> "Headache"
        SymptomType.BACK_LEG_PAIN -> "Back / leg pain"
        SymptomType.OTHER -> "Other symptom"
    }

    fun lastDay(snapshots: List<DaySnapshot>): LocalDate =
        snapshots.map { it.day }.maxOrNull() ?: LocalDate.ofEpochDay(0)

    fun severityPoints(snapshots: List<DaySnapshot>, type: SymptomType): List<InsightPoint> =
        snapshots
            .mapNotNull { snap ->
                val sev = snap.symptoms.firstOrNull { it.type == type }?.severity ?: return@mapNotNull null
                InsightPoint(date = snap.day, value = sev.toDouble())
            }
            .sortedBy { it.date }

    fun frequencyPoints(snapshots: List<DaySnapshot>, predicate: (DaySnapshot) -> Boolean): List<InsightPoint> =
        snapshots
            .map { InsightPoint(date = it.day, value = if (predicate(it)) 1.0 else 0.0) }
            .sortedBy { it.date }
}
