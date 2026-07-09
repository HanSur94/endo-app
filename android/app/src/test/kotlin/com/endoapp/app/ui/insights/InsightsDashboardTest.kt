package com.endoapp.app.ui.insights

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToNode
import com.endoapp.app.content.InsightsCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.insights.InsightsCoordinator
import com.endoapp.app.insights.InsightsUiState
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightConfidence
import com.endoapp.insights.InsightKind
import com.endoapp.insights.InsightPoint
import com.endoapp.insights.TrendWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Pins the gated Insights dashboard (22-UI-SPEC §A) on the JVM Robolectric+Compose lane over a plain
 * [InsightsUiState] + an [onDismiss] closure (no coordinator/store/clock needed for the render proofs).
 * Proves the non-negotiable honesty invariants:
 *   • the five kind sections render in FIXED order with FLARE FIRST (by POSITION only),
 *   • the Empty state shows `insights.empty` — NOT empty sections,
 *   • a suppressed / no-cycle state has `insights.section.cyclePhase` provably ABSENT (omit-when-empty),
 *   • a sparse insufficiency-notice renders `…insufficient` with NO confidence pill,
 *   • the not-medical-advice disclaimer is pinned,
 *   • the "Updating…" caption shows ONLY while updating (non-blocking; cached content stays),
 *   • dismiss is NON-DESTRUCTIVE: it routes the card's `signalKey` to the coordinator (append-only
 *     DismissedInsight), reachable BOTH visually AND via a TalkBack custom action, and deletes NO log.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InsightsDashboardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // --- Fixtures: a plain, structurally-stable insight per kind (copy is neutral — chrome is audited
    // elsewhere; these headline/detail strings are engine-verbatim analogs, not registered chrome). ---

    private fun insight(
        kind: InsightKind,
        signalKey: String,
        confidence: InsightConfidence = InsightConfidence.MEDIUM,
        points: List<InsightPoint> = emptyList(),
        insufficient: Boolean = false,
    ): Insight = Insight.derived(
        kind = kind,
        window = TrendWindow.LAST30,
        signalKey = signalKey,
        headline = "A pattern in your own data",
        detail = "Something to keep an eye on and talk through with your clinician.",
        confidence = confidence,
        points = points,
        sampleSize = 12,
        isInsufficiencyNotice = insufficient,
    )

    private fun oneOfEachKind(): List<Insight> = listOf(
        insight(InsightKind.CORRELATION, "assoc.sleepVsPain"),
        insight(InsightKind.FLARE, "flare.earlyWarning"),
        insight(InsightKind.RECOMMENDATION, "rec.discussSleep"),
        insight(InsightKind.CYCLE_PHASE, "cycle.lutealPain"),
        insight(InsightKind.TREND, "trend.pelvicPain"),
    )

    private fun setDashboard(state: InsightsUiState, onDismiss: (String) -> Unit = {}) {
        composeRule.setContent {
            BloomTheme {
                InsightsDashboard(state = state, onDismiss = onDismiss)
            }
        }
        composeRule.waitForIdle()
    }

    // --- Five sections, FLARE FIRST ---

    @Test
    fun fiveSections_renderInFixedOrder_flareFirst() {
        // The pure section model is the deterministic order/omission proof (off-device, no lazy layout).
        val order = insightSections(oneOfEachKind()).map { it.kind }
        assertEquals(
            "the five kind sections are fixed-order with FLARE FIRST (by position only)",
            listOf(
                InsightKind.FLARE,
                InsightKind.TREND,
                InsightKind.CYCLE_PHASE,
                InsightKind.CORRELATION,
                InsightKind.RECOMMENDATION,
            ),
            order,
        )

        // And every section actually renders in the LazyColumn (scroll each into composition).
        setDashboard(InsightsUiState(insights = oneOfEachKind()))
        listOf(
            "insights.section.flare",
            "insights.section.trends",
            "insights.section.cyclePhase",
            "insights.section.correlations",
            "insights.section.recommendations",
        ).forEach { tag ->
            composeRule.onNodeWithTag("insights.screen").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertExists()
        }
    }

    // --- Empty state (NOT empty sections) ---

    @Test
    fun emptyState_showsWarmEmpty_notEmptySections() {
        setDashboard(InsightsUiState(insights = emptyList()))

        composeRule.onNodeWithTag("insights.empty").assertExists()
        // No kind section renders in the empty state.
        composeRule.onNodeWithTag("insights.section.flare").assertDoesNotExist()
        composeRule.onNodeWithTag("insights.section.trends").assertDoesNotExist()
    }

    // --- Suppressed / no-cycle: the cycle-phase section is provably ABSENT ---

    @Test
    fun suppressedNoCycle_cyclePhaseSectionIsAbsent() {
        val noCycle = listOf(
            insight(InsightKind.FLARE, "flare.earlyWarning"),
            insight(InsightKind.TREND, "trend.pelvicPain"),
            insight(InsightKind.CORRELATION, "assoc.sleepVsPain"),
        )
        // Pure model: cycle-phase is omitted entirely.
        assertFalse(
            "a no-cycle user's sections must not include CYCLE_PHASE",
            insightSections(noCycle).any { it.kind == InsightKind.CYCLE_PHASE },
        )

        setDashboard(InsightsUiState(insights = noCycle))
        // It never composes — the section is omitted, not an empty shell.
        composeRule.onNodeWithTag("insights.section.cyclePhase").assertDoesNotExist()
        // Sanity: a present section still renders.
        composeRule.onNodeWithTag("insights.screen").performScrollToNode(hasTestTag("insights.section.flare"))
        composeRule.onNodeWithTag("insights.section.flare").assertExists()
    }

    // --- Sparse: an insufficiency notice — no confidence pill ---

    @Test
    fun sparseInsufficiencyNotice_rendersInsufficientCard_withNoConfidencePill() {
        val key = "trend.pelvicPain"
        setDashboard(
            InsightsUiState(
                insights = listOf(insight(InsightKind.TREND, key, insufficient = true)),
            ),
        )
        val root = "insights.card.$key"
        composeRule.onNodeWithTag("insights.screen").performScrollToNode(hasTestTag("$root.insufficient"))
        composeRule.onNodeWithTag("$root.insufficient").assertExists()
        // The sparse card carries NO confidence pill.
        composeRule.onNodeWithTag("$root.confidence").assertDoesNotExist()
    }

    // --- The pinned disclaimer ---

    @Test
    fun disclaimer_isPinnedWithTheInsights() {
        setDashboard(InsightsUiState(insights = oneOfEachKind()))
        composeRule.onNodeWithTag("insights.disclaimer").assertExists().assertIsDisplayed()
    }

    // --- Non-blocking "Updating…" caption ---

    @Test
    fun updatingCaption_showsOnlyWhileUpdating() {
        setDashboard(InsightsUiState(insights = oneOfEachKind(), isUpdating = true))
        composeRule.onNodeWithTag("insights.updating").assertExists()
    }

    @Test
    fun updatingCaption_hiddenWhenNotUpdating() {
        setDashboard(InsightsUiState(insights = oneOfEachKind(), isUpdating = false))
        composeRule.onNodeWithTag("insights.updating").assertDoesNotExist()
    }

    // --- Dismiss: routes the signalKey (visual affordance) ---

    @Test
    fun dismiss_visualAffordance_invokesClosureWithSignalKey() {
        val key = "flare.earlyWarning"
        val dismissed = mutableListOf<String>()
        setDashboard(InsightsUiState(insights = listOf(insight(InsightKind.FLARE, key)))) { dismissed.add(it) }

        val root = "insights.card.$key"
        composeRule.onNodeWithTag("insights.screen").performScrollToNode(hasTestTag("$root.dismiss"))
        composeRule.onNodeWithTag("$root.dismiss").performClick()
        composeRule.waitForIdle()

        assertEquals("the visual dismiss routes the card's signalKey", listOf(key), dismissed)
    }

    // --- Dismiss: reachable via the TalkBack custom action (no visual gesture) ---

    @Test
    fun dismiss_talkBackCustomAction_invokesClosureWithSignalKey() {
        val key = "trend.pelvicPain"
        val dismissed = mutableListOf<String>()
        setDashboard(InsightsUiState(insights = listOf(insight(InsightKind.TREND, key)))) { dismissed.add(it) }

        val root = "insights.card.$key"
        composeRule.onNodeWithTag("insights.screen").performScrollToNode(hasTestTag(root))
        composeRule.onNodeWithTag(root).performCustomAccessibilityActionWithLabel(InsightsCopy.DISMISS_ACTION)
        composeRule.waitForIdle()

        assertEquals("the TalkBack custom action routes the same signalKey", listOf(key), dismissed)
    }

    // --- Dismiss is NON-DESTRUCTIVE: routes to the coordinator, writes an append-only dismissal, and
    // deletes NO logged data (the coordinator has NO delete seam — a reversible preference). ---

    @Test
    fun dismiss_isNonDestructive_writesAppendOnlyDismissal_neverDeletesLoggedData() {
        val key = "flare.earlyWarning"
        val written = mutableListOf<String>()
        // A sentinel "logged data" store the dismiss path must never touch. The coordinator exposes no
        // delete closure at all — dismiss can only APPEND a DismissedInsight.
        val loggedData = mutableListOf(DaySnapshot.of(day = LocalDate.ofEpochDay(100)))

        val scope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = InsightsCoordinator(
            loadSnapshots = { loggedData.toList() },
            loadDismissed = { emptySet() },
            loadCache = { emptyList() },
            persist = { _, _, _ -> },
            writeDismissed = { k -> written.add(k) }, // append-only — never a delete
            now = { LocalDate.ofEpochDay(200) },
            nowMillis = { 0L },
            scope = scope,
            engine = { _, _, _ -> emptyList() },
            defaultDispatcher = Dispatchers.Unconfined,
            debounceMillis = 0,
        )

        setDashboard(
            InsightsUiState(insights = listOf(insight(InsightKind.FLARE, key))),
            onDismiss = coordinator::dismiss,
        )

        val root = "insights.card.$key"
        composeRule.onNodeWithTag("insights.screen").performScrollToNode(hasTestTag("$root.dismiss"))
        composeRule.onNodeWithTag("$root.dismiss").performClick()
        composeRule.waitForIdle()

        assertTrue("dismiss appends an append-only DismissedInsight for the signalKey", written.contains(key))
        assertEquals("no logged data is deleted by a dismiss (non-destructive)", 1, loggedData.size)
    }
}
