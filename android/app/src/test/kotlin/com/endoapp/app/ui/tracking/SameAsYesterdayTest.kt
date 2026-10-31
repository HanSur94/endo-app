package com.endoapp.app.ui.tracking

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Same as yesterday" is sticky-CONTEXT-only (ATRK-01, threat T-20-01). It prefills yesterday's meds,
 * sleep, and activity and NEVER carries over symptoms or bleeding — a partially-filled yesterday must
 * not fabricate today's health signals.
 *
 * The core contract is proven purely on [applySameAsYesterday] (no Compose); a second Compose-driven
 * case proves the screen wires the prefill → persist path with the same honesty (persisted rows carry
 * the sticky factors but zero symptom/bleeding rows).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SameAsYesterdayTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** A seeded "yesterday" that has BOTH sticky context AND symptoms/bleeding (the tempting-to-carry). */
    private fun yesterday(): DayDraft = DayDraft(
        symptoms = mapOf(SymptomType.PELVIC_PAIN to 7, SymptomType.FATIGUE to null),
        flow = FlowIntensity.MEDIUM,
        clots = true,
        medsTaken = setOf("med-a", "med-b"),
        tookPRN = true,
        sleepHours = 7.5,
        sleepHoursSet = true,
        sleepQuality = SleepQuality.GOOD,
        activityType = ActivityType.WALKING,
        activityMinutes = 30.0,
        activityIntensity = ActivityIntensity.MEDIUM,
    )

    @Test
    fun applySameAsYesterday_prefillsStickyContext() {
        val result = applySameAsYesterday(current = DayDraft(), yesterday = yesterday())

        // Sticky context carried over verbatim.
        assertEquals(setOf("med-a", "med-b"), result.medsTaken)
        assertTrue(result.tookPRN)
        assertEquals(7.5, result.sleepHours!!, 0.0)
        assertTrue(result.sleepHoursSet)
        assertEquals(SleepQuality.GOOD, result.sleepQuality)
        assertEquals(ActivityType.WALKING, result.activityType)
        assertEquals(30.0, result.activityMinutes!!, 0.0)
        assertEquals(ActivityIntensity.MEDIUM, result.activityIntensity)
    }

    @Test
    fun applySameAsYesterday_neverCarriesSymptomsOrBleeding() {
        val result = applySameAsYesterday(current = DayDraft(), yesterday = yesterday())

        // Symptoms and bleeding are NEVER fabricated from yesterday.
        assertTrue("symptoms must stay empty", result.symptoms.isEmpty())
        assertTrue("pain must stay empty", result.pain.isEmpty())
        assertNull("flow must stay null", result.flow)
        assertNull("clots must stay null", result.clots)
    }

    @Test
    fun applySameAsYesterday_preservesTodaysAlreadyTouchedSymptoms() {
        // A user who already logged a symptom TODAY, then taps Same as yesterday, keeps today's symptom.
        val today = DayDraft(symptoms = mapOf(SymptomType.HEADACHE to 4))

        val result = applySameAsYesterday(current = today, yesterday = yesterday())

        assertEquals(mapOf(SymptomType.HEADACHE to 4), result.symptoms)
        // …while still gaining the sticky context.
        assertEquals(ActivityType.WALKING, result.activityType)
    }

    @Test
    fun screen_sameAsYesterdayThenDone_persistsStickyFactorsButNoSymptomOrBleedingRows() {
        var captured: DayLogRows? = null

        composeRule.setContent {
            BloomTheme {
                DailyCheckInScreen(
                    today = 20_000L,
                    loadYesterday = { yesterday() },
                    persistDay = { captured = it },
                    onDayLogged = {},
                    onDismiss = {},
                    idFor = { "20000:$it" },
                )
            }
        }

        composeRule.onNodeWithTag("checkin.sameAsYesterday").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("checkin.done").performClick()
        composeRule.waitForIdle()

        val rows = requireNotNull(captured) { "persistDay must have been called" }
        // Sticky context persisted (meds x2, prn, sleep, activity ⇒ ≥5 factor rows), zero health signals.
        assertTrue("sticky factors persisted", rows.factors.isNotEmpty())
        assertTrue("no symptom rows carried over", rows.symptoms.isEmpty())
        assertTrue("no pain rows carried over", rows.pain.isEmpty())
        assertTrue("no bleeding rows carried over", rows.bleeding.isEmpty())
    }
}
