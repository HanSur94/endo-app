// Requirement: AREL-01 (Phase 24, D-02) — the REAL `%PDF`-bytes + page-count smoke. Device-gated
// (instrumented) because `android.graphics.pdf.PdfDocument`/`PdfRenderer` are native and unavailable on the
// plain JVM unit lane (RESEARCH Pitfall 2). The JVM `ReportRenderTest` proves the vector/copy/page-count
// invariants off the recording seam; this proves the real renderer emits a well-formed, multi-page PDF file.
//
// Mirrors the existing androidTest precedent (DeleteDayCascadeTest): ApplicationProvider + a real cache dir.
package com.endoapp.app.report

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Emulator/device proof that `ReportPdfRenderer.render` writes a real, well-formed, multi-page VECTOR PDF:
 * the bytes begin with `%PDF-` and `PdfRenderer.pageCount >= 2` for the populated fixture.
 */
@RunWith(AndroidJUnit4::class)
class ReportPdfSmokeTest {

    private val start = LocalDate.of(2026, 5, 1)
    private val end = LocalDate.of(2026, 6, 30)

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

    @Test
    fun render_writesWellFormedMultiPageVectorPdf() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val file = ReportPdfRenderer.render(richModel(), ctx.cacheDir)
        assertNotNull("render must produce a PDF file (never null on a valid model)", file)
        requireNotNull(file)
        try {
            val bytes = file.readBytes()
            assertTrue("PDF is non-trivially sized", bytes.size > 64)
            assertEquals(
                "the written file is a real PDF (%PDF- magic)",
                "%PDF-",
                String(bytes.copyOfRange(0, 5), Charsets.US_ASCII),
            )

            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                assertTrue("the populated fixture spans ≥2 pages", renderer.pageCount >= 2)
            } finally {
                renderer.close()
                pfd.close()
            }
        } finally {
            file.delete()
        }
    }
}
