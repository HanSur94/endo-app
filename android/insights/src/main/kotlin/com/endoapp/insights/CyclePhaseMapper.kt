// EndoInsights (:insights) — the estimated cycle-phase mapper.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/CyclePhaseMapper.swift` (INST-02 / SC1).
// Pure-JVM (no android./androidx.; `java.time.LocalDate` only — the BoundaryLint is the proof). Ports
// `CycleMath`'s derived-cycle math VERBATIM (D-17 mandates identical math — do NOT "improve" it) into the
// Kotlin idiom: Swift's `enum` namespace → a Kotlin `object`; per Pattern 2 the injected `Calendar` is
// DROPPED and `today` is a `LocalDate`; `startOfDay` is a no-op (LocalDate is already a day). Same inputs
// + same injected `today` → an equal `List<Insight>`.
//
// HONESTY — the deliverable IS the omission (the #1 endo pitfall, SC1):
//   • suppressed   → return [] (mirrors `CycleMath`'s `if isSuppressed { return .suppressed }`).
//   • < 2 cycles   → return [] (no phantom phase from a single bleed).
//   • spread > 9   → return [] (irregular: the cycle is too unstable to estimate phases, D-03).
//   • confidence   → caps at MEDIUM, NEVER HIGH — a phase window is an estimate even at best (D-02).
//   • copy         → descriptive, non-causal ("tended to co-occur with") — never trigger/causes/will/because.
package com.endoapp.insights

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure estimated-cycle-phase mapper. No store, no UI, no I/O, no logging — value-in/value-out. */
object CyclePhaseMapper {

    /** The single public entry point (mirrors `CycleMath.summarize` / `TrendsEngine.trends`). */
    fun insights(from: List<DaySnapshot>, asOf: LocalDate): List<Insight> {
        // Re-derive bleeding + suppression self-contained (D-14) — mirrors TrendsEngine.bleedingTrend.
        val bleedingDays = from.filter { it.flow != null }.map { it.day }
        val anySuppressed = from.any { it.suppressed }

        // 1. Suppressed short-circuit (SC1) — emit NOTHING (CycleMath: `if isSuppressed { return .suppressed }`).
        if (anySuppressed) return emptyList()

        // 2–4. runs → period starts → start-to-start gaps.
        val runs = periodRuns(bleedingDays)
        val starts = runs.mapNotNull { it.firstOrNull() }
        val gaps = consecutiveGaps(starts)

        // 3. < 2 cycles (i.e. < 2 start-to-start gaps) → no phantom phase.
        val lastStart = starts.lastOrNull()
        val lastRun = runs.lastOrNull()
        if (gaps.size < 2 || lastStart == null || lastRun == null) return emptyList()

        // 4. median cycle length, regularity spread.
        val median = medianInt(gaps)
        val spread = spread(gaps)

        // 5. confidence gate — `null` for irregular (spread > 9) → no phases (D-03).
        val confidence = phaseConfidence(cycleCount = gaps.size, spread = spread) ?: return emptyList()

        // Build the four estimated phase windows for the CURRENT cycle (the one starting at lastStart).
        val windows = phaseWindows(lastStart = lastStart, lastRun = lastRun, median = median, spread = spread)

        // Label every in-range snapshot day with its phase, then emit one descriptive insight per
        // (phase, symptom) with a logged co-occurrence.
        return associations(from, windows = windows, confidence = confidence)
    }

    // MARK: - Ported VERBATIM from CycleMath (D-17 — identical math; do NOT alter).

    /** Group flow days into runs of consecutive calendar days. Consecutive days collapse into ONE run
     *  (a period), not N starts. De-duplicates same-day entries. Returns runs ascending by start. */
    fun periodRuns(days: List<LocalDate>): List<List<LocalDate>> {
        val sorted = days.toSortedSet().toList()
        if (sorted.isEmpty()) return emptyList()

        val runs = mutableListOf<List<LocalDate>>()
        var current = mutableListOf(sorted[0])
        for (d in sorted.drop(1)) {
            val prev = current.last()
            val dayGap = ChronoUnit.DAYS.between(prev, d).toInt()
            if (dayGap == 1) {
                current.add(d) // consecutive → same run
            } else {
                runs.add(current) // a gap > 1 day → a new period run
                current = mutableListOf(d)
            }
        }
        runs.add(current)
        return runs
    }

    /** Day gaps between consecutive period-start dates. `[]` for fewer than two starts. */
    fun consecutiveGaps(starts: List<LocalDate>): List<Int> {
        if (starts.size < 2) return emptyList()
        return starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
    }

    /** Median of integer gaps (sort + middle; mean of the two middles for an even count). */
    fun medianInt(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        val mid = s.size / 2
        if (s.size % 2 == 1) return s[mid]
        return (s[mid - 1] + s[mid]) / 2
    }

    /** Regularity measure: the spread (max − min) of the gaps. 0 → perfectly regular. */
    fun spread(values: List<Int>): Int {
        val lo = values.minOrNull() ?: return 0
        val hi = values.maxOrNull() ?: return 0
        return hi - lo
    }

    // MARK: - Phase confidence (A2 — caps at MEDIUM, NEVER HIGH; null = too irregular → no phases).

    /** More cycles + lower spread → higher confidence, but CAPPED at MEDIUM because a phase window is an
     *  estimate even at best (D-01/D-02). Returning `null` is the explicit "too irregular, omit mapping"
     *  decision (the irregular gate, D-03):
     *    - `(c ≥ 3 && spread ≤ 3)` → MEDIUM  (a tight, multi-cycle history)
     *    - `(spread ≤ 9)`          → LOW     (a usable but looser estimate)
     *    - else                    → null    (spread > 9 → irregular → NO phases) */
    fun phaseConfidence(cycleCount: Int, spread: Int): InsightConfidence? = when {
        cycleCount >= 3 && spread <= 3 -> InsightConfidence.MEDIUM
        spread <= 9 -> InsightConfidence.LOW
        else -> null
    }

    // MARK: - Estimated phase windows (DST-safe day math — never epoch arithmetic).

    /** A day range a phase covers (inclusive lower, inclusive upper). */
    data class PhaseWindow(
        val phase: CyclePhase,
        val lower: LocalDate,
        val upper: LocalDate,
    )

    /** The four estimated phase windows for the current cycle, relative to the last period start `S`,
     *  median cycle length `M`, and an ovulation `band = max(2, spread / 2)` that WIDENS with irregularity
     *  (D-02 "wide uncertainty band"). Ovulation is anchored 14 days BEFORE the next predicted start
     *  `S + M` (the luteal phase is the biologically stable ~14-day anchor, A7). */
    fun phaseWindows(lastStart: LocalDate, lastRun: List<LocalDate>, median: Int, spread: Int): List<PhaseWindow> {
        val s0 = lastStart
        val band = maxOf(2, spread / 2)

        fun add(days: Int, to: LocalDate): LocalDate = to.plusDays(days.toLong())

        // The menstrual window is the actual recorded run (honest — not a fixed count).
        val bleedEnd = lastRun.maxOrNull() ?: s0

        val nextStart = add(median, s0) // S + M
        val ovCenter = add(-14, nextStart) // S + M - 14
        val ovLower = add(-band, ovCenter) // S + M - 14 - band
        val ovUpper = add(band, ovCenter) // S + M - 14 + band

        // follicular starts the day AFTER bleeding ends; ends the day BEFORE the ovulation band.
        val folLower = add(1, bleedEnd)
        val folUpper = add(-1, ovLower)
        // luteal starts the day AFTER the ovulation band; ends the day BEFORE the next predicted start.
        val lutLower = add(1, ovUpper)
        val lutUpper = add(-1, nextStart)

        val windows = mutableListOf(
            PhaseWindow(phase = CyclePhase.MENSTRUAL, lower = s0, upper = bleedEnd),
            PhaseWindow(phase = CyclePhase.OVULATORY, lower = ovLower, upper = ovUpper),
        )
        // Guard against degenerate (inverted) follicular/luteal ranges for very short cycles.
        if (folLower <= folUpper) windows.add(PhaseWindow(phase = CyclePhase.FOLLICULAR, lower = folLower, upper = folUpper))
        if (lutLower <= lutUpper) windows.add(PhaseWindow(phase = CyclePhase.LUTEAL, lower = lutLower, upper = lutUpper))

        // WR-05: overlap resolution must be EXPLICIT and order-INDEPENDENT. If ANY constructed window pair
        // overlaps, the cycle is too short to phase-map honestly: return [] (consistent with the irregular
        // short-circuit, D-03), rather than silently mis-attributing days.
        if (windowsOverlap(windows)) return emptyList()
        return windows
    }

    /** True if ANY two of the constructed phase windows share a day (inclusive ranges). Order-independent —
     *  it compares every unordered pair, so it does not depend on array insertion order (WR-05). */
    fun windowsOverlap(windows: List<PhaseWindow>): Boolean {
        for (i in windows.indices) {
            for (j in windows.indices) {
                if (j <= i) continue
                // Inclusive ranges overlap iff each starts on or before the other's end.
                if (windows[i].lower <= windows[j].upper && windows[j].lower <= windows[i].upper) {
                    return true
                }
            }
        }
        return false
    }

    /** The estimated phase a calendar day falls in, or `null` if it is outside the current cycle's windows.
     *  Windows are guaranteed non-overlapping by `phaseWindows` (WR-05), so `first` is unambiguous here. */
    fun phase(day: LocalDate, windows: List<PhaseWindow>): CyclePhase? {
        return windows.firstOrNull { it.lower <= day && day <= it.upper }?.phase
    }

    // MARK: - Per-(phase, symptom) descriptive association emission (non-causal copy, D-08/D-16).

    /** Label each in-range snapshot day with its phase, then emit ONE descriptive `CYCLE_PHASE` Insight
     *  per (phase, symptom) with a logged co-occurrence. Copy is NON-CAUSAL — "tended to co-occur with" —
     *  never trigger/causes/will/because. Output is deterministic (sorted by phase then symptom). */
    fun associations(snapshots: List<DaySnapshot>, windows: List<PhaseWindow>, confidence: InsightConfidence): List<Insight> {
        // Per-phase: total in-phase days, and per-symptom co-occurrence counts.
        val dayCount = mutableMapOf<CyclePhase, Int>()
        val symptomCount = mutableMapOf<CyclePhase, MutableMap<SymptomType, Int>>()

        for (snap in snapshots) {
            val phase = phase(snap.day, windows) ?: continue
            dayCount[phase] = (dayCount[phase] ?: 0) + 1
            for (symptom in snap.symptoms.map { it.type }.toSet()) {
                val perPhase = symptomCount.getOrPut(phase) { mutableMapOf() }
                perPhase[symptom] = (perPhase[symptom] ?: 0) + 1
            }
        }

        val out = mutableListOf<Insight>()
        for (phase in CyclePhase.entries) { // deterministic phase order
            val totalDays = dayCount[phase] ?: 0
            val counts = symptomCount[phase]
            if (totalDays <= 0 || counts == null) continue
            // Deterministic symptom order.
            for (type in counts.keys.sortedBy { it.raw }) {
                val logged = counts[type] ?: 0
                // Emit the candidate at its REAL evidence count; the HonestyGate enforces the n>=2 floor.
                if (logged < 1) continue
                val label = displayName(type)
                val phaseLabel = phaseName(phase)
                out.add(
                    Insight.derived(
                        kind = InsightKind.CYCLE_PHASE,
                        window = TrendWindow.ALL_TIME,
                        signalKey = "cyclePhase.${phase.raw}.symptom.${type.raw}",
                        // Descriptive + non-causal: a logged co-occurrence count only. The copy avoids the
                        // forbidden causal substrings ("cause" ⊂ "because") — a plain co-occurrence.
                        headline = "$label tended to co-occur with your $phaseLabel phase",
                        detail = "$label was logged on $logged of $totalDays of your estimated $phaseLabel-phase days — a descriptive co-occurrence only.",
                        confidence = confidence,
                        points = emptyList(),
                        sampleSize = logged,
                    ),
                )
            }
        }
        return out
    }

    // MARK: - Labels (package-local; mirrors TrendsEngine.displayName).

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

    fun phaseName(phase: CyclePhase): String = when (phase) {
        CyclePhase.MENSTRUAL -> "menstrual"
        CyclePhase.FOLLICULAR -> "follicular"
        CyclePhase.OVULATORY -> "ovulatory"
        CyclePhase.LUTEAL -> "luteal"
    }
}
