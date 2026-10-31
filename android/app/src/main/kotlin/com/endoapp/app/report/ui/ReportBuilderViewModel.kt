// Requirement: AREL-01 / AREL-02 (Phase 24-05, D-01) — the report BUILDER brain: where the pure model
// (Plan 02), the vector renderer (Plan 03), and the share host (Plan 04) compose into the user-facing
// "Export report" flow. The Android analog of the iOS `ReportBuilderView`'s view-model logic.
//
// SEAM-DRIVEN / PURE-TESTABLE: every collaborator is an INJECTED closure — the repository reads
// (snapshots / per-day provenance / suppressed / dismissed), the LOCKED `:insights` engine, the PDF
// renderer, and the clock. So the whole generate flow runs on the JVM/Robolectric lane with NO Room, NO
// real clock, and NO real PDF device — a fake `render` returns a `File` (or `null`) and the state machine
// is asserted directly (mirrors the Phase-23 `ExportImportViewModel` posture).
//
// HONEST FAILURE (T-24-06): a null/failed render transitions to [ReportPhase.Error] (the honest error
// state) — NEVER a blank/zero-byte PDF, NEVER a leaked exception. The renderer already logs only the
// error TYPE; this layer surfaces the calm [ReportCopy.ERROR_*] copy.
//
// SEPARATION INVARIANT: this file imports NO notification / alarm / WorkManager / networking API — the
// reminders package stays the app's SOLE notification owner and the report path stays network-free
// (ReportNoNetworkTest scans the whole `com.endoapp.app.report` package, incl. `report/ui`).
package com.endoapp.app.report.ui

import com.endoapp.app.data.DaySnapshotSource
import com.endoapp.app.report.RangePreset
import com.endoapp.app.report.ReportModel
import com.endoapp.app.report.ReportRangeMapping
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightsEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * The phase of a report generation. Deliberately small: the screen shows the range picker for [Idle], the
 * "Generating…" caption + spinner for [Generating], the Share action for [ReadyToShare], and the calm
 * error banner for [Error] (its copy comes from [com.endoapp.app.content.ReportCopy]).
 */
sealed interface ReportPhase {
    /** No generation running — the screen shows the range picker + Generate. */
    data object Idle : ReportPhase

    /** A generation is in flight (assemble + render, off-main) — the screen shows the spinner + caption. */
    data object Generating : ReportPhase

    /** A PDF was produced — the screen shows the Share action for [file] (in `cacheDir/reports/`). */
    data class ReadyToShare(val file: File) : ReportPhase

    /** Assemble/render failed — the screen shows the honest error banner (never a blank PDF). */
    data object Error : ReportPhase
}

/**
 * The observable builder state: the current range selection ([preset] + the [customFrom]/[customTo]
 * custom span) plus the generation [phase]. [isRangeValid] and [isGenerateEnabled] are DERIVED off the
 * pure [ReportRangeMapping.isValid] guard so the invalid-range message + the disabled Generate stay in
 * lock-step with the model's own validity rule (a custom `To` before `From` is invalid).
 */
data class ReportBuilderUiState(
    val preset: RangePreset,
    val customFrom: LocalDate,
    val customTo: LocalDate,
    val phase: ReportPhase = ReportPhase.Idle,
) {
    /** True unless the CUSTOM range is inverted (`To` precedes `From`) — presets are always valid. */
    val isRangeValid: Boolean get() = ReportRangeMapping.isValid(preset, customFrom, customTo)

    /** Generate is enabled only for a valid range that is not already generating. */
    val isGenerateEnabled: Boolean get() = isRangeValid && phase !is ReportPhase.Generating
}

/**
 * The builder view-model. A plain class (manual composition, no DI framework — the app's established
 * seam posture), so it stays trivially JVM-testable: every dependency is an injected function.
 *
 * ## generate ([generate])
 * Generating → resolve the [com.endoapp.app.report.ReportRange] via [ReportRangeMapping.interval] →
 * gather the pure inputs off the injected repository seams (all snapshots; the per-in-range-day
 * provenance map; `suppressed`; `dismissed`) → [ReportModel.assemble] with the LOCKED engine injected as
 * the closure → [render] → ReadyToShare(file) on success, else Error. The assemble + render run OFF the
 * caller's thread via [withContext] on [dispatcher] (never block the UI thread).
 *
 * @param loadSnapshots the consent-gated projected day snapshots (EndoRepository::daySnapshots).
 * @param loadSources the per-day provenance batch read for the in-range days (EndoRepository::daySnapshotSources).
 * @param loadSuppressed the D-09 suppressed / no-cycle flag (latest TreatmentState).
 * @param loadDismissed the dismissed signal-keys to exclude from the engine (EndoRepository::dismissedSignalKeys).
 * @param render the PDF renderer (ReportPdfRenderer::render over the app cacheDir) — returns the file or null.
 * @param engine the LOCKED `:insights` gate (InsightsEngine::insights); `dismissed` is bound in per run.
 * @param now the clock seam (injected so tests need no real clock) — anchors the preset ranges.
 * @param dispatcher the background dispatcher the assemble + render run on (default [Dispatchers.Default]).
 */
class ReportBuilderViewModel(
    private val loadSnapshots: suspend () -> List<DaySnapshot>,
    private val loadSources: suspend (List<Long>) -> Map<Long, DaySnapshotSource>,
    private val loadSuppressed: suspend () -> Boolean,
    private val loadDismissed: suspend () -> Set<String>,
    private val render: suspend (ReportModel) -> File?,
    private val engine: (List<DaySnapshot>, LocalDate, Set<String>) -> List<Insight> = InsightsEngine::insights,
    private val now: () -> LocalDate = LocalDate::now,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val _state = MutableStateFlow(
        ReportBuilderUiState(
            preset = RangePreset.LAST30,
            // A sensible initial custom span (last 30 days) — the user adjusts it in the picker.
            customFrom = now().minusDays(30),
            customTo = now(),
        ),
    )

    /** The live builder state the screen collects. */
    val state: StateFlow<ReportBuilderUiState> = _state.asStateFlow()

    /** Select a range [preset] (clears any prior terminal phase back to Idle so the picker is live). */
    fun selectPreset(preset: RangePreset) {
        _state.value = _state.value.copy(preset = preset, phase = ReportPhase.Idle)
    }

    /** Update the CUSTOM range span; the derived [ReportBuilderUiState.isGenerateEnabled] reacts. */
    fun setCustomRange(from: LocalDate, to: LocalDate) {
        _state.value = _state.value.copy(customFrom = from, customTo = to, phase = ReportPhase.Idle)
    }

    /** Reset to the picker (e.g. after a share completes or the user dismisses an error). */
    fun reset() {
        _state.value = _state.value.copy(phase = ReportPhase.Idle)
    }

    /**
     * Generate the PDF for the current selection. Sets [ReportPhase.Generating], assembles the pure
     * [ReportModel] off the injected seams, renders it OFF the main thread, and transitions to
     * [ReportPhase.ReadyToShare] on a produced file or [ReportPhase.Error] on a null/failed render or any
     * thrown collaborator failure (the honest error — never a blank PDF, never a leaked exception).
     */
    suspend fun generate() {
        val selection = _state.value
        if (!selection.isRangeValid) return // guarded in UI too; defensive here
        _state.value = selection.copy(phase = ReportPhase.Generating)
        try {
            val asOf = now()
            val range = ReportRangeMapping.interval(
                preset = selection.preset,
                from = selection.customFrom,
                to = selection.customTo,
                asOf = asOf,
            )
            val snapshots = loadSnapshots()
            // The per-in-range-day provenance (keyed by epoch-day at the repo boundary, mapped to LocalDate
            // for the pure ReportModel). Only the days the report covers are read.
            val inRangeDays = snapshots
                .map { it.day }
                .filter { !it.isBefore(range.start) && !it.isAfter(range.end) }
                .map { it.toEpochDay() }
            val sourcesByDay = loadSources(inRangeDays)
                .mapKeys { (epochDay, _) -> LocalDate.ofEpochDay(epochDay) }
            val suppressed = loadSuppressed()
            val dismissed = loadDismissed()

            // Assemble (pure) + render (PDF) OFF the caller's thread — never block the UI thread.
            val model = withContext(dispatcher) {
                ReportModel.assemble(
                    range = range,
                    snapshots = snapshots,
                    sourcesByDay = sourcesByDay,
                    suppressed = suppressed,
                    engine = { snaps, engineAsOf -> engine(snaps, engineAsOf, dismissed) },
                    asOf = range.end,
                )
            }
            val file = withContext(dispatcher) { render(model) }
            _state.value = _state.value.copy(
                phase = if (file != null) ReportPhase.ReadyToShare(file) else ReportPhase.Error,
            )
        } catch (_: Throwable) {
            // Any collaborator failure is the honest "we couldn't build the report" — never a leaked detail.
            _state.value = _state.value.copy(phase = ReportPhase.Error)
        }
    }
}
