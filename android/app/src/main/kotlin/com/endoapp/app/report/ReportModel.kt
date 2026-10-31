// Requirement: AREL-01 / AREL-02 (Phase 24, D-04/D-08) — the PURE, testable report core. Kotlin port of
// the assembly logic in the iOS `Sources/EndoApp/Features/Report/ReportModel.swift`.
//
// `ReportModel` is the honesty + source-label boundary of the doctor-ready PDF, assembled WITHOUT any
// rendering. It is the ONE place where range-filtered store reads + the LOCKED `:insights` engine become
// a flat, Equatable value model the unit tests assert against — decoupling "is the report honest?" from
// "does it draw?" (Plan 05 renders this).
//
// HONESTY CONTRACT (the endo pitfall):
//   • RANGE SCOPING (A1): descriptive sections honor the EXACT chosen range (computed from range-filtered
//     snapshots); the insights section surfaces only engine windows whose span ⊆ the range, anchored at
//     `asOf`. The engine is window-based, so we filter the array rather than pass a range.
//   • absence ≠ 0: a lifestyle factor NOT logged is `null` → "Not logged", NEVER 0. A logged 0 is a real 0.
//   • NO phantom cycle: the cycle section is a THREE-STATE type from the PURE `CycleMath` (never re-derived)
//     — a suppressed user shows the off-state, never a fabricated cycle-day/window.
//   • sparse → "Not enough data in this range": a sub-threshold range yields the honest empty state.
//   • PER-SECTION SOURCE LABELS (AREL-02): each section's source is AGGREGATED from the Plan-01
//     `DaySnapshotSource` merge gate across the range's days — NEVER recomputed from values.
//
// PURE: `java.time.LocalDate` only — NO Room, NO coroutine, NO clock, NO networking. The engine is
// INJECTED as a closure so `assemble` stays a synchronous value-in / value-out function, trivially
// JVM-testable. Fixed copy (the renderer's strings) lives in `ReportCopy`, never inline here.
package com.endoapp.app.report

import com.endoapp.app.data.DaySnapshotSource
import com.endoapp.app.data.ValueSource
import com.endoapp.app.domain.CycleMath
import com.endoapp.app.domain.CycleSummary
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightPoint
import com.endoapp.insights.SymptomType
import java.time.LocalDate

/**
 * The PURE, Equatable report model — the honesty + source-label boundary (AREL-01/02). Assembled from
 * range-filtered snapshots + the injected (LOCKED) engine, with NO rendering.
 */
data class ReportModel(
    /** The exact date range this report covers. */
    val range: ReportRange,
    /** When the report was assembled (the injected anchor). */
    val generatedOn: LocalDate,
    /** Section 1 — Header. */
    val header: HeaderSection,
    /** Section 2 — Summary / coverage. */
    val summary: SummarySection,
    /** Section 3 — Symptoms & pain (sparse → not-enough-data). */
    val symptoms: SymptomsSection,
    /** Section 4 — Cycle & bleeding (three-state, NO phantom cycle). */
    val cycle: CycleSection,
    /** Section 4 provenance: aggregated from the in-range `flow` sources (self-reported vs imported). */
    val cycleSource: SectionSource,
    /** Section 5 — Lifestyle factors (per factor: value OR null → "Not logged"; absence ≠ 0). */
    val lifestyle: LifestyleSection,
    /** Section 6 — Insights: the engine output for the range (windows ⊆ range, anchored at asOf). */
    val insights: List<Insight>,
    /** Section 7 — Source legend. */
    val legend: SourceLegend,
) {
    companion object {

        /**
         * The minimum distinct logged days a symptom needs for the descriptive trend section to read as
         * "enough data" — kept EQUAL to the LOCKED engine's `HonestyGate.minSample(TREND) == 3` so the
         * report's honesty floor and the engine's never drift.
         */
        private const val MIN_SYMPTOM_TREND_DAYS = 3

        /**
         * Assemble the pure report for a date range. PURE: reads only the injected values and calls the
         * injected [engine] closure — no store, no clock, no I/O.
         *
         * @param range the exact range the descriptive sections honor (inclusive of [ReportRange.end]).
         * @param snapshots the projected day snapshots (any order; filtered + sorted here).
         * @param sourcesByDay per-day provenance keyed by calendar day (the Plan-01 merge gate). A missing
         *   day → no provenance (all-null [DaySnapshotSource]).
         * @param suppressed the D-09 suppressed / no-cycle flag (drives the off-state).
         * @param engine the LOCKED `:insights` gate, injected so the call site supplies `dismissed`. It
         *   receives the RANGE-FILTERED snapshots and the [asOf] anchor — never an arbitrary range.
         * @param asOf the anchor the engine's windows measure back from (the call site passes `range.end`).
         */
        fun assemble(
            range: ReportRange,
            snapshots: List<DaySnapshot>,
            sourcesByDay: Map<LocalDate, DaySnapshotSource>,
            suppressed: Boolean,
            engine: (List<DaySnapshot>, LocalDate) -> List<Insight>,
            asOf: LocalDate,
        ): ReportModel {
            // RANGE SCOPING (A1): filter to the range INCLUSIVE of range.end, sorted ascending by day so
            // "latest wins" in the lifestyle fold is deterministic. Every descriptive section is computed
            // from THIS filtered set so it honors the EXACT range independent of the engine's windows.
            val inRange = snapshots
                .filter { dayInRange(it.day, range) }
                .sortedBy { it.day }

            // The per-in-range-day provenance, aligned to `inRange` order (a missing day → all-null).
            val sourceList = inRange.map { sourcesByDay[it.day] ?: DaySnapshotSource() }

            // SECTION 6 — Insights (A1): hand the RANGE-FILTERED snapshots to the LOCKED engine anchored at
            // asOf. Only windows ⊆ the range contribute; the HonestyGate is reused verbatim (not here).
            val insights = engine(inRange, asOf)

            // SECTION 1 — Header.
            val header = HeaderSection(range = range, generatedOn = asOf)

            // SECTION 4 — Cycle (NO phantom cycle): feed the RANGE-FILTERED flow days + suppressed into the
            // PURE CycleMath (never re-derive), then map onto the three-state CycleSection EXACTLY.
            // bleedingInRange derives from snapshot.flow (absence ≠ 0; no separate bleeding read).
            val bleedingInRange = inRange.filter { it.flow != null }.map { it.day }
            val cycleSummary = CycleMath.summarize(
                bleedingDays = bleedingInRange,
                isSuppressed = suppressed,
                today = range.end,
            )
            val cycle = cycleSection(cycleSummary)

            // SECTION 4 provenance: the cycle rests on bleeding/flow, so its source aggregates the in-range
            // `flow` provenance. NEVER hardcoded — consistent with the other sections.
            val cycleSource = SectionSource.aggregate(sourceList.map { it.flow })

            // SECTION 3 — Symptoms (descriptive trend table; absolute, never cycle-relative).
            val symptoms = symptomsSection(inRange)

            // SECTION 5 — Lifestyle (absence ≠ 0): each field is the LATEST logged value in range or null.
            val lifestyle = lifestyleSection(inRange, sourceList)

            // SECTION 2 — Summary / coverage: distinct in-range logged days + the aggregate source. Include
            // symptom provenance so the legend explains the "Self-reported" badge the symptoms section
            // draws (symptoms have no dedicated DaySnapshotSource field — manual-only today).
            val symptomFieldSources: List<ValueSource?> =
                inRange.map { if (it.symptoms.isEmpty()) null else ValueSource.MANUAL }
            val allFieldSources: List<ValueSource?> =
                sourceList.flatMap { listOf(it.flow, it.sleep, it.activity, it.food) } + symptomFieldSources
            val summary = SummarySection(
                daysLogged = inRange.size,
                source = SectionSource.aggregate(allFieldSources),
            )

            // SECTION 7 — Legend: surface only the labels actually in use across the report.
            val presentSources = allFieldSources.filterNotNull()
            val legend = SourceLegend(
                hasSelfReported = presentSources.contains(ValueSource.MANUAL),
                hasHealthConnect = presentSources.contains(ValueSource.HEALTH_CONNECT),
            )

            return ReportModel(
                range = range,
                generatedOn = asOf,
                header = header,
                summary = summary,
                symptoms = symptoms,
                cycle = cycle,
                cycleSource = cycleSource,
                lifestyle = lifestyle,
                insights = insights,
                legend = legend,
            )
        }

        // --- Pure assembly helpers ---

        /** A day is in range when it falls within `[start, end]` INCLUSIVE of `end`'s calendar day. */
        private fun dayInRange(day: LocalDate, range: ReportRange): Boolean =
            !day.isBefore(range.start) && !day.isAfter(range.end)

        /**
         * Map a [CycleSummary] onto the three-state [CycleSection] EXACTLY (the no-phantom-cycle contract):
         * suppressed → off-state; !hasEnoughData → notEnoughData; else derived.
         */
        private fun cycleSection(summary: CycleSummary): CycleSection = when {
            summary.isSuppressed -> CycleSection.Suppressed
            !summary.hasEnoughData -> CycleSection.NotEnoughData
            else -> CycleSection.Derived(
                cycleDay = summary.cycleDay,
                nextWindow = summary.nextPeriod,
                confidence = summary.confidence,
            )
        }

        /**
         * Build the descriptive symptoms section from the range-filtered snapshots. One row per symptom
         * type logged in range, with null-honest min/max severity (present-without-severity stays null). A
         * range with NO logged symptoms is honestly empty (`hasEnoughData == false`).
         */
        private fun symptomsSection(inRange: List<DaySnapshot>): SymptomsSection {
            // Per type: nil-honest min/max + distinct logged-day count; and the REAL per-day series (the
            // day's MAX recorded severity, keyed by day). A day logged WITHOUT a severity counts toward
            // `days` but NOT the chart series (no fabricated value) — honest gaps.
            data class Agg(var min: Int?, var max: Int?, val days: MutableSet<LocalDate>)

            val byType = LinkedHashMap<SymptomType, Agg>()
            val dailyMax = HashMap<SymptomType, MutableMap<LocalDate, Int>>()

            for (snap in inRange) {
                for (point in snap.symptoms) {
                    val agg = byType.getOrPut(point.type) { Agg(null, null, mutableSetOf()) }
                    agg.days.add(snap.day)
                    val sev = point.severity
                    if (sev != null) {
                        agg.min = agg.min?.let { minOf(it, sev) } ?: sev
                        agg.max = agg.max?.let { maxOf(it, sev) } ?: sev
                        val perDay = dailyMax.getOrPut(point.type) { mutableMapOf() }
                        perDay[snap.day] = perDay[snap.day]?.let { maxOf(it, sev) } ?: sev
                    }
                }
            }

            val rows = byType
                .map { (type, agg) ->
                    val points = (dailyMax[type] ?: emptyMap())
                        .map { InsightPoint(date = it.key, value = it.value.toDouble()) }
                        .sortedBy { it.date }
                    SymptomRow(
                        type = type,
                        minSeverity = agg.min,
                        maxSeverity = agg.max,
                        daysLogged = agg.days.size,
                        points = points,
                    )
                }
                .sortedBy { it.type.raw } // deterministic order for Equatable fixtures

            // HONEST SPARSE GATE: only meaningful with the SAME floor the LOCKED engine uses (3 distinct
            // logged days). A range whose busiest symptom is logged on fewer reads as "not enough data".
            val busiestSymptomDays = rows.maxOfOrNull { it.daysLogged } ?: 0
            val hasEnoughData = busiestSymptomDays >= MIN_SYMPTOM_TREND_DAYS

            // Symptom provenance has no dedicated DaySnapshotSource field; symptoms are manual-only today,
            // so a day with ANY logged symptom contributes MANUAL; aggregate honestly (empty → NONE).
            val symptomSources: List<ValueSource?> =
                inRange.map { if (it.symptoms.isEmpty()) null else ValueSource.MANUAL }

            return SymptomsSection(
                rows = rows,
                hasEnoughData = hasEnoughData,
                source = SectionSource.aggregate(symptomSources),
            )
        }

        /**
         * Build the lifestyle section (absence ≠ 0). Each field is the LATEST logged value across the
         * in-range days (sorted ascending, so the last non-null wins) or null ("Not logged"). A logged
         * 0/level is a real value.
         */
        private fun lifestyleSection(
            inRange: List<DaySnapshot>,
            sources: List<DaySnapshotSource>,
        ): LifestyleSection {
            var sleepHours: Double? = null
            var sleepQuality: com.endoapp.insights.SleepQuality? = null
            var activityType: com.endoapp.insights.ActivityType? = null
            var activityMinutes: Double? = null
            var loggedFood = false
            var stress: Int? = null
            var mood: Int? = null

            for (snap in inRange) {
                snap.sleepHours?.let { sleepHours = it }
                snap.sleepQuality?.let { sleepQuality = it }
                snap.activityType?.let { activityType = it }
                snap.activityMinutes?.let { activityMinutes = it }
                if (snap.foodTags.isNotEmpty()) loggedFood = true
                snap.stress?.let { stress = it } // a logged 0 sets a real 0 (absence ≠ 0)
                snap.mood?.let { mood = it }
            }

            // Aggregate the lifestyle provenance from the imported-capable fields (sleep/activity/food).
            val lifestyleSources = sources.flatMap { listOf(it.sleep, it.activity, it.food) }

            return LifestyleSection(
                sleepHours = sleepHours,
                sleepQuality = sleepQuality,
                activityType = activityType,
                activityMinutes = activityMinutes,
                loggedFood = loggedFood,
                stress = stress,
                mood = mood,
                source = SectionSource.aggregate(lifestyleSources),
            )
        }
    }
}
