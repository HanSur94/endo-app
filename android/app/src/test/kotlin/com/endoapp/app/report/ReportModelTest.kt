package com.endoapp.app.report

import com.endoapp.app.data.DaySnapshotSource
import com.endoapp.app.data.ValueSource
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightConfidence
import com.endoapp.insights.InsightKind
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomPoint
import com.endoapp.insights.SymptomType
import com.endoapp.insights.TrendWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The Kotlin port of the iOS `ReportModelTests` (Phase 24, D-04/D-08). Proves the pure `ReportModel`
 * honesty boundary in isolation — NO Room, NO clock, NO coroutine: every snapshot / provenance /
 * suppressed / engine input is injected, so `assemble` is a deterministic value-in / value-out function.
 *
 * Honesty invariants under test: range scoping (out-of-range excluded, end inclusive), absence ≠ 0,
 * sparse → not-enough-data, no-phantom-cycle (three-state from `CycleMath`), per-source labels aggregated
 * (not recomputed), and the injected engine carried verbatim.
 */
class ReportModelTest {

    // A no-op engine (most honesty cases don't exercise the insights section).
    private val noEngine: (List<DaySnapshot>, LocalDate) -> List<Insight> = { _, _ -> emptyList() }

    private fun snap(
        day: LocalDate,
        symptoms: List<SymptomPoint> = emptyList(),
        flow: FlowIntensity? = null,
        sleepHours: Double? = null,
        sleepQuality: SleepQuality? = null,
        stress: Int? = null,
        mood: Int? = null,
    ): DaySnapshot = DaySnapshot.of(
        day = day,
        symptoms = symptoms,
        flow = flow,
        sleepHours = sleepHours,
        sleepQuality = sleepQuality,
        stress = stress,
        mood = mood,
    )

    // --- SectionSource.aggregate (the merge gate rule) ---

    @Test
    fun sectionSource_aggregate_classifiesEmptyMixedManualImported() {
        assertEquals(SectionSource.NONE, SectionSource.aggregate(listOf(null, null)))
        assertEquals(SectionSource.SELF_REPORTED, SectionSource.aggregate(listOf(ValueSource.MANUAL, null)))
        assertEquals(
            SectionSource.HEALTH_CONNECT,
            SectionSource.aggregate(listOf(ValueSource.HEALTH_CONNECT, null)),
        )
        assertEquals(
            SectionSource.MIXED,
            SectionSource.aggregate(listOf(ValueSource.MANUAL, ValueSource.HEALTH_CONNECT)),
        )
    }

    // --- Empty range ---

    @Test
    fun emptyRange_isHonestOnEverySection() {
        val range = ReportRange(start = LocalDate.of(2026, 6, 1), end = LocalDate.of(2026, 6, 30))
        // A snapshot exists, but OUTSIDE the range → nothing is in-range.
        val outside = snap(LocalDate.of(2026, 1, 1), stress = 3)

        val model = ReportModel.assemble(
            range = range,
            snapshots = listOf(outside),
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )

        assertEquals(0, model.summary.daysLogged)
        assertTrue(model.symptoms.rows.isEmpty())
        assertFalse(model.symptoms.hasEnoughData)
        assertEquals(CycleSection.NotEnoughData, model.cycle)
        assertNull(model.lifestyle.stress)
        assertEquals(SectionSource.NONE, model.summary.source)
        assertFalse(model.legend.hasSelfReported)
        assertFalse(model.legend.hasHealthConnect)
        assertTrue(model.insights.isEmpty())
    }

    // --- Range scoping ---

    @Test
    fun rangeScoping_excludesOutOfRange_includesTheEndDay() {
        val range = ReportRange(start = LocalDate.of(2026, 6, 1), end = LocalDate.of(2026, 6, 10))
        val fatigue = listOf(SymptomPoint(SymptomType.FATIGUE, severity = 4))
        val before = snap(LocalDate.of(2026, 5, 31), symptoms = fatigue) // excluded
        val onEnd = snap(LocalDate.of(2026, 6, 10), symptoms = fatigue) // INCLUDED (end inclusive)
        val after = snap(LocalDate.of(2026, 6, 11), symptoms = fatigue) // excluded

        val model = ReportModel.assemble(
            range = range,
            snapshots = listOf(before, onEnd, after),
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )

        // Only the end-day snapshot is in range → 1 logged day, and its symptom row has 1 logged day.
        assertEquals(1, model.summary.daysLogged)
        assertEquals(1, model.symptoms.rows.single().daysLogged)
    }

    @Test
    fun engine_receivesTheRangeFilteredSnapshots_andAsOf() {
        val range = ReportRange(start = LocalDate.of(2026, 6, 1), end = LocalDate.of(2026, 6, 10))
        val inA = snap(LocalDate.of(2026, 6, 2), stress = 1)
        val inB = snap(LocalDate.of(2026, 6, 9), stress = 1)
        val out = snap(LocalDate.of(2026, 7, 1), stress = 1)

        var seenCount = -1
        var seenAsOf: LocalDate? = null
        val carried = Insight.derived(
            kind = InsightKind.TREND,
            window = TrendWindow.LAST7,
            signalKey = "symptom.fatigue.severity",
            headline = "Fatigue pattern",
            detail = "Seen across your logged days.",
            confidence = InsightConfidence.LOW,
            points = emptyList(),
            sampleSize = 5,
        )
        val spyEngine: (List<DaySnapshot>, LocalDate) -> List<Insight> = { snaps, asOf ->
            seenCount = snaps.size
            seenAsOf = asOf
            listOf(carried)
        }

        val model = ReportModel.assemble(
            range = range,
            snapshots = listOf(inA, inB, out),
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = spyEngine,
            asOf = range.end,
        )

        assertEquals("engine sees only the 2 in-range snapshots", 2, seenCount)
        assertEquals("engine anchored at range.end", range.end, seenAsOf)
        assertEquals("the engine output is carried verbatim", listOf(carried), model.insights)
    }

    // --- Sparse symptoms gate (min 3 distinct logged days) ---

    @Test
    fun symptoms_onTwoDays_isNotEnough_onThreeDays_isEnough() {
        val range = ReportRange(start = LocalDate.of(2026, 6, 1), end = LocalDate.of(2026, 6, 30))
        val pt = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, severity = 5))

        val twoDays = ReportModel.assemble(
            range = range,
            snapshots = listOf(snap(LocalDate.of(2026, 6, 2), symptoms = pt), snap(LocalDate.of(2026, 6, 4), symptoms = pt)),
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )
        assertFalse("busiest symptom on 2 days → not enough", twoDays.symptoms.hasEnoughData)

        val threeDays = ReportModel.assemble(
            range = range,
            snapshots = listOf(
                snap(LocalDate.of(2026, 6, 2), symptoms = pt),
                snap(LocalDate.of(2026, 6, 4), symptoms = pt),
                snap(LocalDate.of(2026, 6, 6), symptoms = pt),
            ),
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )
        assertTrue("busiest symptom on 3 days → enough", threeDays.symptoms.hasEnoughData)
        val row = threeDays.symptoms.rows.single()
        assertEquals(3, row.daysLogged)
        assertEquals(5, row.minSeverity)
        assertEquals(5, row.maxSeverity)
        assertEquals("one real per-day point per logged-with-severity day", 3, row.points.size)
    }

    @Test
    fun symptoms_loggedWithoutSeverity_keepsMinMaxNull_butStillCountsTheDay() {
        val range = ReportRange(start = LocalDate.of(2026, 6, 1), end = LocalDate.of(2026, 6, 30))
        val noSev = listOf(SymptomPoint(SymptomType.BRAIN_FOG, severity = null))
        val model = ReportModel.assemble(
            range = range,
            snapshots = listOf(snap(LocalDate.of(2026, 6, 3), symptoms = noSev)),
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )
        val row = model.symptoms.rows.single()
        assertEquals(1, row.daysLogged)
        assertNull("present-without-severity → min stays null (absence ≠ 0)", row.minSeverity)
        assertNull(row.maxSeverity)
        assertTrue("no fabricated chart point without a severity", row.points.isEmpty())
    }

    // --- absence ≠ 0 ---

    @Test
    fun lifestyle_unloggedStressIsNull_loggedZeroIsARealZero() {
        val range = ReportRange(start = LocalDate.of(2026, 6, 1), end = LocalDate.of(2026, 6, 30))

        val unlogged = ReportModel.assemble(
            range = range,
            snapshots = listOf(snap(LocalDate.of(2026, 6, 5), mood = 3)), // stress NOT logged
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )
        assertNull("unlogged stress → null (never 0)", unlogged.lifestyle.stress)

        val loggedZero = ReportModel.assemble(
            range = range,
            snapshots = listOf(snap(LocalDate.of(2026, 6, 5), stress = 0)),
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )
        assertEquals("a logged 0 is a real 0 (absence ≠ 0)", 0, loggedZero.lifestyle.stress)
    }

    // --- No phantom cycle (three states) ---

    @Test
    fun cycle_suppressed_isTheOffState() {
        val range = ReportRange(start = LocalDate.of(2026, 3, 1), end = LocalDate.of(2026, 5, 1))
        val model = ReportModel.assemble(
            range = range,
            snapshots = listOf(snap(LocalDate.of(2026, 3, 1), flow = FlowIntensity.MEDIUM)),
            sourcesByDay = emptyMap(),
            suppressed = true,
            engine = noEngine,
            asOf = range.end,
        )
        assertEquals(CycleSection.Suppressed, model.cycle)
    }

    @Test
    fun cycle_underTwoCycles_isNotEnoughData() {
        val range = ReportRange(start = LocalDate.of(2026, 3, 1), end = LocalDate.of(2026, 5, 1))
        val model = ReportModel.assemble(
            range = range,
            snapshots = listOf(snap(LocalDate.of(2026, 3, 1), flow = FlowIntensity.MEDIUM)),
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )
        assertEquals(CycleSection.NotEnoughData, model.cycle)
    }

    @Test
    fun cycle_regularHistory_isDerivedWithCycleDayAndNextWindow() {
        // Three period starts 28 days apart → 2 gaps → derived. today = lastStart + 5 → cycleDay 6.
        val s0 = LocalDate.of(2026, 3, 1)
        val s1 = LocalDate.of(2026, 3, 29)
        val s2 = LocalDate.of(2026, 4, 26)
        val end = s2.plusDays(5) // May 1
        val range = ReportRange(start = s0, end = end)
        val flow = FlowIntensity.MEDIUM
        val snaps = listOf(snap(s0, flow = flow), snap(s1, flow = flow), snap(s2, flow = flow))
        // flow provenance MANUAL on each bleeding day → cycleSource SELF_REPORTED.
        val sources = mapOf(
            s0 to DaySnapshotSource(flow = ValueSource.MANUAL),
            s1 to DaySnapshotSource(flow = ValueSource.MANUAL),
            s2 to DaySnapshotSource(flow = ValueSource.MANUAL),
        )

        val model = ReportModel.assemble(
            range = range,
            snapshots = snaps,
            sourcesByDay = sources,
            suppressed = false,
            engine = noEngine,
            asOf = end,
        )

        val cycle = model.cycle
        assertTrue("regular history → Derived", cycle is CycleSection.Derived)
        cycle as CycleSection.Derived
        assertEquals(6, cycle.cycleDay)
        assertTrue("next-window is a non-null date range", cycle.nextWindow != null)
        assertTrue("confidence is never HIGH", cycle.confidence <= InsightConfidence.MEDIUM)
        assertEquals("cycle source aggregates the manual flow provenance", SectionSource.SELF_REPORTED, model.cycleSource)
    }

    // --- Per-source labels ---

    @Test
    fun perSource_mixedField_aggregatesToMixed() {
        val range = ReportRange(start = LocalDate.of(2026, 6, 1), end = LocalDate.of(2026, 6, 30))
        val d1 = LocalDate.of(2026, 6, 3)
        val d2 = LocalDate.of(2026, 6, 4)
        // Both days log sleep; one manual, one from Health Connect → lifestyle source MIXED.
        val snaps = listOf(
            snap(d1, sleepHours = 7.0, sleepQuality = SleepQuality.GOOD),
            snap(d2, sleepHours = 6.5, sleepQuality = SleepQuality.OKAY),
        )
        val sources = mapOf(
            d1 to DaySnapshotSource(sleep = ValueSource.MANUAL),
            d2 to DaySnapshotSource(sleep = ValueSource.HEALTH_CONNECT),
        )
        val model = ReportModel.assemble(
            range = range,
            snapshots = snaps,
            sourcesByDay = sources,
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )
        assertEquals(SectionSource.MIXED, model.lifestyle.source)
        assertTrue(model.legend.hasSelfReported)
        assertTrue(model.legend.hasHealthConnect)
    }

    @Test
    fun perSource_symptomsOnlyManualRange_isSelfReportedWithLegend() {
        val range = ReportRange(start = LocalDate.of(2026, 6, 1), end = LocalDate.of(2026, 6, 30))
        val pt = listOf(SymptomPoint(SymptomType.FATIGUE, severity = 3))
        val snaps = listOf(
            snap(LocalDate.of(2026, 6, 2), symptoms = pt),
            snap(LocalDate.of(2026, 6, 4), symptoms = pt),
        )
        // No DaySnapshotSource entries — symptoms are manual-only, defined by the symptom presence.
        val model = ReportModel.assemble(
            range = range,
            snapshots = snaps,
            sourcesByDay = emptyMap(),
            suppressed = false,
            engine = noEngine,
            asOf = range.end,
        )
        assertEquals(SectionSource.SELF_REPORTED, model.symptoms.source)
        assertTrue("legend defines the Self-reported badge the symptoms section draws", model.legend.hasSelfReported)
        assertFalse(model.legend.hasHealthConnect)
    }
}
