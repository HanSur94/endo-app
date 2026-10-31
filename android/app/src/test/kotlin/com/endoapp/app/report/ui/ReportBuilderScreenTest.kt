package com.endoapp.app.report.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.endoapp.app.content.ReportCopy
import com.endoapp.app.data.DaySnapshotSource
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.report.RangePreset
import com.endoapp.insights.DaySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * The report builder behaviors (AREL-01/02, threats T-24-11 / T-24-12 / T-24-01) on the JVM
 * Robolectric+Compose lane. The SCREEN is driven ENTIRELY off a hand-set [MutableStateFlow] + captured
 * seams (no real OS chooser, no real PDF); the VIEWMODEL is exercised over injected closures (no Room,
 * no clock, no PDF device):
 *
 * 1. An invalid custom range (To before From) → Generate DISABLED + the invalid-range message shown.
 * 2. A valid custom range → Generate ENABLED; tapping it invokes the generate seam.
 * 3. ReadyToShare → the Share action forwards to the share seam.
 * 4. Generating → the "Generating…" caption is shown (the real chooser never appears).
 * 5. The ViewModel: generate() over fake seams → ReadyToShare on a produced file, Error on a null render.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportBuilderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val anchor: LocalDate = LocalDate.of(2026, 6, 9)

    private fun state(
        preset: RangePreset,
        from: LocalDate = anchor.minusDays(10),
        to: LocalDate = anchor,
        phase: ReportPhase = ReportPhase.Idle,
    ) = MutableStateFlow(
        ReportBuilderUiState(preset = preset, customFrom = from, customTo = to, phase = phase),
    )

    // --- 1. Invalid custom range → Generate disabled + invalid-range message (T-24-12) ---

    @Test
    fun invalidCustomRange_disablesGenerate_andShowsInvalidMessage() {
        // To (day-5) precedes From (day-0) → inverted → invalid.
        composeRule.setContent {
            BloomTheme {
                ReportBuilderScreen(
                    state = state(RangePreset.CUSTOM, from = anchor, to = anchor.minusDays(5)),
                )
            }
        }

        composeRule.onNodeWithTag("report.invalidRange").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ReportCopy.INVALID_RANGE).assertIsDisplayed()
        composeRule.onNodeWithTag("report.generate").assertIsNotEnabled()
    }

    // --- 2. Valid range → Generate enabled + invokes the generate seam (T-24-11 reachability) ---

    @Test
    fun validRange_enablesGenerate_andTapInvokesGenerateSeam() {
        var generated = 0
        composeRule.setContent {
            BloomTheme {
                ReportBuilderScreen(
                    state = state(RangePreset.CUSTOM, from = anchor.minusDays(5), to = anchor),
                    onGenerate = { generated++ },
                )
            }
        }

        composeRule.onNodeWithTag("report.invalidRange").assertDoesNotExist()
        composeRule.onNodeWithTag("report.generate").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("report.generate").performClick()
        composeRule.waitForIdle()

        assertEquals("tapping Generate invokes the generate seam", 1, generated)
    }

    @Test
    fun presets_areOffered_andSelectable() {
        var selected: RangePreset? = null
        composeRule.setContent {
            BloomTheme {
                ReportBuilderScreen(
                    state = state(RangePreset.LAST30),
                    onSelectPreset = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag("report.preset.last30").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("report.preset.last90").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("report.preset.custom").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("tapping a preset chip selects it", RangePreset.CUSTOM, selected)
        // A preset (never invalid) enables Generate.
        composeRule.onNodeWithTag("report.generate").performScrollTo().assertIsEnabled()
    }

    // --- 3. ReadyToShare → the Share action forwards to the share seam (T-24-01 egress) ---

    @Test
    fun readyToShare_showsShareAction_andForwardsToShareSeam() {
        var shared = 0
        val file = File.createTempFile("endo-report", ".pdf")
        composeRule.setContent {
            BloomTheme {
                ReportBuilderScreen(
                    state = state(RangePreset.LAST30, phase = ReportPhase.ReadyToShare(file)),
                    onShare = { shared++ },
                )
            }
        }

        composeRule.onNodeWithTag("report.share").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("report.share").performClick()
        composeRule.waitForIdle()

        assertEquals("tapping Share forwards to the share seam", 1, shared)
    }

    // --- 4. Generating → the caption is shown; the real chooser never appears ---

    @Test
    fun generating_showsCaption() {
        composeRule.setContent {
            BloomTheme {
                ReportBuilderScreen(state = state(RangePreset.LAST30, phase = ReportPhase.Generating))
            }
        }

        composeRule.onNodeWithTag("report.status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ReportCopy.GENERATING_CAPTION).assertIsDisplayed()
    }

    @Test
    fun error_showsHonestErrorHeading() {
        composeRule.setContent {
            BloomTheme {
                ReportBuilderScreen(state = state(RangePreset.LAST30, phase = ReportPhase.Error))
            }
        }

        composeRule.onNodeWithText(ReportCopy.ERROR_HEADING).performScrollTo().assertIsDisplayed()
    }

    // --- 5. The ViewModel over injected closures ONLY (no Room, no clock, no PDF device) ---

    private fun viewModel(
        snapshots: List<DaySnapshot> = emptyList(),
        render: suspend () -> File?,
    ) = ReportBuilderViewModel(
        loadSnapshots = { snapshots },
        loadSources = { days -> days.associateWith { DaySnapshotSource() } },
        loadSuppressed = { false },
        loadDismissed = { emptySet() },
        render = { render() },
        engine = { _, _, _ -> emptyList() },
        now = { anchor },
    )

    @Test
    fun viewModel_generate_producesReadyToShare_onAProducedFile() = runTest {
        val pdf = File.createTempFile("endo-report", ".pdf")
        val vm = viewModel(render = { pdf })

        assertTrue("presets are valid by default", vm.state.value.isGenerateEnabled)
        vm.generate()

        val phase = vm.state.value.phase
        assertTrue("a produced file → ReadyToShare", phase is ReportPhase.ReadyToShare)
        assertEquals(pdf, (phase as ReportPhase.ReadyToShare).file)
    }

    @Test
    fun viewModel_generate_producesError_onNullRender() = runTest {
        val vm = viewModel(render = { null })
        vm.generate()
        assertEquals("a null render → honest Error", ReportPhase.Error, vm.state.value.phase)
    }

    @Test
    fun viewModel_invertedCustomRange_isNotGenerateEnabled() {
        val vm = viewModel(render = { null })
        vm.selectPreset(RangePreset.CUSTOM)
        vm.setCustomRange(from = anchor, to = anchor.minusDays(3)) // inverted
        assertTrue("an inverted custom range is invalid", !vm.state.value.isRangeValid)
        assertTrue("…so Generate is disabled", !vm.state.value.isGenerateEnabled)
    }

    @Test
    fun viewModel_generate_isNoOp_onInvalidRange() = runTest {
        val vm = viewModel(render = { error("render must not be called for an invalid range") })
        vm.selectPreset(RangePreset.CUSTOM)
        vm.setCustomRange(from = anchor, to = anchor.minusDays(3))
        vm.generate()
        assertNull(
            "an invalid range never renders (stays Idle)",
            (vm.state.value.phase as? ReportPhase.ReadyToShare),
        )
        assertEquals(ReportPhase.Idle, vm.state.value.phase)
    }
}
