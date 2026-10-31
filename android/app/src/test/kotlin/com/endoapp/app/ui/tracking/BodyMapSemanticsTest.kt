package com.endoapp.app.ui.tracking

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.insights.BodyRegion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The accessible-BodyMap contract (SYMP-02, threat T-20-03) proven on the semantics tree (JVM
 * Robolectric+Compose lane):
 *
 * 1. Every one of the ten [BodyRegion]s exposes a focusable ≥48dp `Role.Button` hotspot with a
 *    non-empty (neutral, non-gendered) contentDescription + a "severity N of 10"/"not marked"
 *    stateDescription + a `bodymap.region.<raw>` testTag (front set + back set combined = all ten).
 * 2. The non-visual "All areas (list)" fallback lists all ten regions (`bodymap.list.<raw>`), each a
 *    Role.Button carrying the same semantics.
 * 3. Completing the map THROUGH the list fallback sets the SAME `DayDraft.pain` point as a hotspot
 *    would — the visual and non-visual paths are one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BodyMapSemanticsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val frontRegions = listOf(
        BodyRegion.SHOULDER, BodyRegion.CHEST_DIAPHRAGM, BodyRegion.LOWER_ABDOMEN_PELVIS,
        BodyRegion.LOWER_QUADRANT_LEFT, BodyRegion.LOWER_QUADRANT_RIGHT, BodyRegion.BLADDER,
        BodyRegion.UPPER_THIGHS,
    )
    private val backRegions = listOf(BodyRegion.LOWER_BACK, BodyRegion.HIPS, BodyRegion.RECTAL_BOWEL)

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
    private fun hasState(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    /** Drive a node's click via its semantics OnClick action (works regardless of on-screen position). */
    private fun clickAction(tag: String) =
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)

    private fun setBodyMap(): () -> DayDraft {
        val draftState = mutableStateOf(DayDraft())
        composeRule.setContent {
            BloomTheme {
                BodyMapView(
                    draft = draftState.value,
                    onSetRegion = { region, sev ->
                        draftState.value = draftState.value.copy(
                            pain = draftState.value.pain.toMutableMap().apply { put(region, sev) },
                        )
                    },
                    onClearRegion = { region ->
                        draftState.value = draftState.value.copy(
                            pain = draftState.value.pain.toMutableMap().apply { remove(region) },
                        )
                    },
                )
            }
        }
        return { draftState.value }
    }

    private fun assertHotspot(region: BodyRegion, expectedState: String) {
        val label = TrackingCopy.bodyRegionLabels.getValue(region)
        composeRule.onNodeWithTag("bodymap.region.${region.raw}")
            .assertExists()
            .assert(hasRole(Role.Button))
            .assert(hasContentDescription(label))
            .assert(hasState(expectedState))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun allTenRegions_exposeFocusableHotspots_withRequiredSemantics() {
        setBodyMap()

        // Front side: its 7 hotspots are present with the required a11y contract.
        frontRegions.forEach { assertHotspot(it, TrackingCopy.NOT_MARKED) }

        // Toggle to the back side and assert its 3 hotspots — front + back = all ten regions.
        composeRule.onNodeWithTag("bodymap.side.back").performClick()
        composeRule.waitForIdle()
        backRegions.forEach { assertHotspot(it, TrackingCopy.NOT_MARKED) }
    }

    @Test
    fun listFallback_listsAllTenRegions_asButtons_withSemantics() {
        setBodyMap()

        clickAction("bodymap.listFallback")
        composeRule.waitForIdle()

        BodyRegion.entries.forEach { region ->
            val label = TrackingCopy.bodyRegionLabels.getValue(region)
            composeRule.onNodeWithTag("bodymap.list.${region.raw}")
                .assertExists()
                .assert(hasRole(Role.Button))
                .assert(hasContentDescription(label))
                .assert(hasState(TrackingCopy.NOT_MARKED))
        }
    }

    @Test
    fun touchingAClusteredHotspotCentre_marksThatRegion_notAnOverlappingNeighbour() {
        // The pelvic cluster's ≥48dp boxes overlap; a real touch at each centre must reach its own region.
        val clustered = listOf(
            BodyRegion.LOWER_ABDOMEN_PELVIS, BodyRegion.LOWER_QUADRANT_LEFT,
            BodyRegion.LOWER_QUADRANT_RIGHT, BodyRegion.BLADDER,
        )
        val readDraft = setBodyMap()

        clustered.forEach { region ->
            composeRule.onNodeWithTag("bodymap.region.${region.raw}").performClick()
            composeRule.waitForIdle()
            clickAction("daylog.severityPreset.moderate")
            composeRule.waitForIdle()
            clickAction("bodymap.region.${region.raw}.done")
            composeRule.waitForIdle()
        }

        assertEquals(clustered.associateWith { 6 }, readDraft().pain)
    }

    @Test
    fun completingThroughTheList_setsTheSameDraftPoint_asAHotspot() {
        val readDraft = setBodyMap()
        val region = BodyRegion.RECTAL_BOWEL // a back-only region — reachable ONLY via the list on front

        // Open the region's severity sheet through the non-visual list, then pick "Moderate" (6).
        clickAction("bodymap.listFallback")
        composeRule.waitForIdle()
        clickAction("bodymap.list.${region.raw}")
        composeRule.waitForIdle()
        clickAction("daylog.severityPreset.moderate")
        composeRule.waitForIdle()

        // The SAME DayDraft.pain point is set (visual and non-visual paths are one).
        assertEquals(6, readDraft().pain[region])
        // And the list row's stateDescription now reflects the marked severity.
        composeRule.onNodeWithTag("bodymap.list.${region.raw}")
            .assert(hasState(TrackingCopy.severityStateDescription(6)))
    }
}
