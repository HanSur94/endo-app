// Requirement: AREL-01 (Phase 24) — the SELECTABLE-VECTOR + exact-copy + two-pass page-count proof, run
// entirely off the pure `RecordingReportCanvas` on the fast JVM lane (RESEARCH Pitfall 1/2). Because Android
// has no first-party PDF text extractor and the written bytes are font-subset + Flate-compressed, we prove
// selectability structurally: the renderer draws every fixed string through `drawText`/`drawWrapped` (VECTOR)
// and — by construction — there is NO raster-text (`drawBitmap`) member on the seam. The real `%PDF`-bytes +
// pageCount smoke is the instrumented `ReportPdfSmokeTest` (device-gated).
package com.endoapp.app.report

import com.endoapp.app.content.Disclaimer
import com.endoapp.app.content.ReportCopy
import com.endoapp.app.data.DaySnapshotSource
import com.endoapp.app.data.ValueSource
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightConfidence
import com.endoapp.insights.InsightKind
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomPoint
import com.endoapp.insights.SymptomType
import com.endoapp.insights.TrendWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportRenderTest {

    private val start = LocalDate.of(2026, 5, 1)
    private val end = LocalDate.of(2026, 6, 30)

    /** A rich, mixed-provenance fixture: ≥3 pelvic-pain days (enough-data + a real chart series), a manual
     *  sleep day + a Health-Connect sleep day (so BOTH legend labels render), and ten insights so the
     *  document spans ≥2 pages (a genuine two-pass page-count test). */
    private fun richModel(): ReportModel {
        fun day(offset: Int) = start.plusDays(offset.toLong())
        fun pelvic(sev: Int) = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, sev))

        val snaps = listOf(
            DaySnapshot.of(day = day(0), symptoms = pelvic(3), sleepHours = 7.0, sleepQuality = SleepQuality.GOOD),
            DaySnapshot.of(day = day(1), symptoms = pelvic(5)),
            DaySnapshot.of(day = day(2), symptoms = pelvic(4), sleepHours = 6.5, sleepQuality = SleepQuality.OKAY),
            DaySnapshot.of(day = day(4), symptoms = pelvic(6)),
            DaySnapshot.of(day = day(6), symptoms = listOf(SymptomPoint(SymptomType.FATIGUE, 2)), stress = 3, mood = 3),
        )
        // day 0 sleep is MANUAL, day 2 sleep is HEALTH_CONNECT → legend shows Self-reported AND Health Connect.
        val sources = mapOf(
            day(0) to DaySnapshotSource(sleep = ValueSource.MANUAL),
            day(2) to DaySnapshotSource(sleep = ValueSource.HEALTH_CONNECT),
        )
        val engine: (List<DaySnapshot>, LocalDate) -> List<Insight> = { _, _ ->
            (1..10).map { i ->
                Insight.derived(
                    kind = InsightKind.TREND,
                    window = TrendWindow.LAST30,
                    signalKey = "signal.$i",
                    headline = "Pattern $i observed in your logged days",
                    detail = "A longer descriptive detail line for pattern $i, spanning enough width to matter.",
                    confidence = InsightConfidence.LOW,
                    points = emptyList(),
                    sampleSize = 5,
                )
            }
        }
        return ReportModel.assemble(
            range = ReportRange(start, end),
            snapshots = snaps,
            sourcesByDay = sources,
            suppressed = false,
            engine = engine,
            asOf = end,
        )
    }

    // Two-pass: pass 1 learns the total; pass 2 stamps footers with that total. The recording spy measures
    // deterministically, so both passes produce the IDENTICAL page count (RESEARCH Pitfall 3).
    private fun pass(model: ReportModel, totalPages: Int): RecordingReportCanvas =
        RecordingReportCanvas().also { ReportPdfRenderer.layout(model, it, totalPages) }

    @Test
    fun disclaimerBand_drawsNotMedicalAdvice_asVectorText_verbatim() {
        val model = richModel()
        val total = ReportPdfRenderer.layout(model, RecordingReportCanvas(), totalPages = 0)
        val rec = pass(model, total)

        // The prominent band reuses Disclaimer.text VERBATIM — proven by both the substring and the exact text.
        assertTrue("disclaimer must-contain", rec.texts.any { it.contains("not medical advice") })
        assertTrue("disclaimer drawn verbatim", rec.texts.any { it.contains(Disclaimer.text) })
    }

    @Test
    fun recordsExactSectionHeadings_asVectorText() {
        val rec = pass(richModel(), totalPages = 3)
        for (heading in listOf(
            ReportCopy.SECTION_SUMMARY_TITLE,
            ReportCopy.SECTION_SYMPTOMS_TITLE,
            ReportCopy.SECTION_CYCLE_TITLE,
            ReportCopy.SECTION_LIFESTYLE_TITLE,
            ReportCopy.SECTION_INSIGHTS_TITLE,
            ReportCopy.SECTION_LEGEND_TITLE,
            ReportCopy.PDF_TITLE,
        )) {
            assertTrue("heading '$heading' drawn as text", rec.texts.contains(heading))
        }
    }

    @Test
    fun mixedProvenanceFixture_rendersBothSourceLabels() {
        val rec = pass(richModel(), totalPages = 3)
        assertTrue(
            "Self-reported label present",
            rec.texts.any { it.contains(ReportCopy.SOURCE_SELF_REPORTED) },
        )
        assertTrue(
            "from Health Connect label present",
            rec.texts.any { it.contains(ReportCopy.SOURCE_HEALTH_CONNECT) },
        )
    }

    @Test
    fun symptomsTable_andEmbeddedChart_areBothDrawn() {
        val rec = pass(richModel(), totalPages = 3)
        // The chart is never the sole carrier of meaning — the table headers render AND the chart is drawn.
        assertTrue(rec.texts.contains(ReportCopy.SYMPTOMS_COL_SYMPTOM))
        assertTrue(rec.texts.contains(ReportCopy.SYMPTOMS_COL_RANGE))
        assertTrue(rec.texts.contains(ReportCopy.SYMPTOMS_COL_DAYS))
        assertTrue("the embedded trend chart is drawn", rec.chartCount >= 1)
    }

    @Test
    fun reportCanvas_hasNoRasterTextPath_byConstruction() {
        // STRUCTURAL anti-forgery: the seam exposes the vector `drawText` path and NO `drawBitmap`-for-text
        // member — a raster text path cannot exist (T-24-07).
        val members = ReportCanvas::class.java.methods.map { it.name.lowercase() }
        assertTrue("the vector drawText path exists", members.contains("drawtext"))
        assertTrue("the vector drawWrapped path exists", members.contains("drawwrapped"))
        assertTrue("no drawBitmap-for-text member", members.none { it.contains("bitmap") })
    }

    @Test
    fun twoPass_pageCountAgrees_footerTotalEqualsProducedPages() {
        val model = richModel()
        val total = ReportPdfRenderer.layout(model, RecordingReportCanvas(), totalPages = 0)
        val rec = RecordingReportCanvas()
        val produced = ReportPdfRenderer.layout(model, rec, totalPages = total)

        assertEquals("pass 1 total == pass 2 produced (no drift)", total, produced)
        assertTrue("the fixture is genuinely multi-page", produced >= 2)

        // Every stamped footer says "Page n of m" with m == the actual produced page count (Pitfall 3).
        val footerTotals = rec.texts.mapNotNull { t ->
            Regex("""Page \d+ of (\d+)""").find(t)?.groupValues?.get(1)?.toInt()
        }
        assertEquals("one footer per page", produced, footerTotals.size)
        assertTrue("every footer total == produced pages", footerTotals.all { it == produced })
    }
}
