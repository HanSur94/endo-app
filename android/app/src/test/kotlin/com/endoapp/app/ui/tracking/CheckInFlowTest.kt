package com.endoapp.app.ui.tracking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.endoapp.app.content.DayLogCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.insights.BodyRegion
import com.endoapp.insights.SymptomType
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Daily Check-In fast-log honesty behaviors (ATRK-01, threats T-20-01 / T-20-02c) on the JVM
 * Robolectric+Compose lane:
 *
 * 1. An EMPTY confirm records ONLY the DayEntry adherence marker (no symptom/factor/bleeding rows —
 *    absence ≠ 0, invents nothing) and shows the inline "Checked in for today." note.
 * 2. Confirming fires the reminder auto-suppress ([onDayLogged]) as an INDEPENDENT effect and does not
 *    block dismissal — dismissal happens even when persistence is still in-flight.
 * 3. The check-in sources import NO notification / alarm / WorkManager API (separation invariant).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckInFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyConfirm_recordsOnlyTheMarker_andShowsTheNote() {
        var captured: DayLogRows? = null

        composeRule.setContent {
            BloomTheme {
                DailyCheckInScreen(
                    today = 20_000L,
                    loadYesterday = { null },
                    persistDay = { captured = it },
                    onDayLogged = {},
                    onDismiss = {},
                    idFor = { "20000:$it" },
                )
            }
        }

        // Confirm with NOTHING touched.
        composeRule.onNodeWithTag("checkin.done").performClick()
        composeRule.waitForIdle()

        val rows = requireNotNull(captured) { "persistDay must have been called on confirm" }
        // Exactly the DayEntry anchor for today — and ZERO invented rows.
        assertEquals("marker records today", 20_000L, rows.dayEntry.epochDay)
        assertTrue("no symptom rows", rows.symptoms.isEmpty())
        assertTrue("no pain rows", rows.pain.isEmpty())
        assertTrue("no bleeding rows", rows.bleeding.isEmpty())
        assertTrue("no factor rows", rows.factors.isEmpty())

        // The honest adherence note is shown.
        composeRule.onNodeWithTag("checkin.confirmNote").assertExists()
    }

    @Test
    fun confirm_firesOnDayLoggedIndependently_andDoesNotBlockDismissal() {
        var onDayLoggedCount = 0
        var dismissCount = 0

        composeRule.setContent {
            BloomTheme {
                DailyCheckInScreen(
                    today = 20_000L,
                    // A non-null yesterday so tapping "Same as yesterday" makes the draft non-empty
                    // (sticky factors) → the immediate-dismiss path.
                    loadYesterday = { DayDraft(tookPRN = true, sleepHours = 8.0, sleepHoursSet = true) },
                    // Persistence never completes — dismissal must not wait on it.
                    persistDay = { suspendCancellableCoroutine<Unit> { } },
                    onDayLogged = { onDayLoggedCount++ },
                    onDismiss = { dismissCount++ },
                    idFor = { "20000:$it" },
                )
            }
        }

        composeRule.onNodeWithTag("checkin.sameAsYesterday").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("checkin.done").performClick()
        composeRule.waitForIdle()

        // onDayLogged fired exactly once, and dismissal happened despite the never-completing persist.
        assertEquals("reminder auto-suppress fired independently", 1, onDayLoggedCount)
        assertEquals("dismissal never blocked on persistence", 1, dismissCount)
    }

    @Test
    fun checkInSources_importNoNotificationOrAlarmApi() {
        val sources = listOf(
            "ui/tracking/DailyCheckInScreen.kt",
            "ui/tracking/CheckInSections.kt",
            "content/CheckInCopy.kt",
        ).map(::resolveSource)

        val forbidden = listOf("notification", "alarm", "workmanager", "androidx.work")
        for (file in sources) {
            val importLines = file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.lowercase() }
            for (line in importLines) {
                val hit = forbidden.firstOrNull { line.contains(it) }
                assertTrue(
                    "Check-in source ${file.name} must import no notification/alarm API (found \"$hit\" in \"$line\").",
                    hit == null,
                )
            }
        }
    }

    /** Resolve a source file relative to the module dir (tests run with cwd = the :app module dir). */
    private fun resolveSource(relative: String): File {
        val base = "src/main/kotlin/com/endoapp/app"
        val candidates = listOf(
            File("$base/$relative"),
            File("android/app/$base/$relative"),
            File(System.getProperty("user.dir"), "$base/$relative"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relative (cwd=${System.getProperty("user.dir")})")
    }

    @Test
    fun painAreaSection_staysHidden_untilAPainLocationSymptomIsSelected() {
        composeRule.setContent {
            BloomTheme {
                DailyCheckInScreen(
                    today = 20_000L,
                    loadYesterday = { null },
                    persistDay = {},
                    onDayLogged = {},
                    onDismiss = {},
                    idFor = { "20000:$it" },
                )
            }
        }

        // No pain-location symptom selected → no pain-area map. (Section headers render UPPERCASE.)
        composeRule.onNodeWithText(DayLogCopy.SECTION_PAIN.uppercase()).assertDoesNotExist()

        composeRule.onNodeWithTag("checkin.symptom.${SymptomType.PELVIC_PAIN.raw}").performClick()
        composeRule.waitForIdle()

        // Pelvic/abdominal pain is a pain-location symptom → the accessible body map appears.
        composeRule.onNodeWithText(DayLogCopy.SECTION_PAIN.uppercase()).assertExists()
        composeRule.onNodeWithTag("checkin.bodyMap").assertExists()
    }

    @Test
    fun markingAPainRegionThroughTheBodyMap_persistsThePainPointOnConfirm() {
        var captured: DayLogRows? = null
        composeRule.setContent {
            BloomTheme {
                DailyCheckInScreen(
                    today = 20_000L,
                    loadYesterday = { null },
                    persistDay = { captured = it },
                    onDayLogged = {},
                    onDismiss = {},
                    idFor = { "20000:$it" },
                )
            }
        }

        composeRule.onNodeWithTag("checkin.symptom.${SymptomType.PELVIC_PAIN.raw}").performClick()
        composeRule.waitForIdle()

        // Mark the lower-abdomen region through the map (hotspot click + Moderate preset).
        val region = BodyRegion.LOWER_ABDOMEN_PELVIS
        composeRule.onNodeWithTag("bodymap.region.${region.raw}")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        // The preset tag exists twice (the check-in's own symptom SeverityControl + the region
        // sheet's) — the sheet renders after the base content, so index 1 is the sheet's preset.
        composeRule.onAllNodesWithTag("daylog.severityPreset.moderate")[1]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("checkin.done").performClick()
        composeRule.waitForIdle()

        val rows = requireNotNull(captured) { "persistDay must have been called on confirm" }
        assertEquals("exactly one pain point must persist", 1, rows.pain.size)
        assertEquals("the pain point must carry the marked region", region.raw, rows.pain.first().regionRaw)
        assertEquals("the pain point must carry severity 6", 6, rows.pain.first().severity)
    }
}
