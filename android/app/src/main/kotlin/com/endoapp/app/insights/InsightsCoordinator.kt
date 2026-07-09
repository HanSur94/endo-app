// D-01/D-03/D-04/D-10/D-13 / INST-02 — the insights integration brain (the Kotlin mirror of the iOS
// `InsightsCoordinator`, per CONTEXT §"Dashboard behavior" + STATE `[08-02]`).
//
// An app-scoped PLAIN class (NOT a ViewModel — ARCHITECTURE prefers a composition-root holder; the iOS
// analog is a plain `@Observable`). It owns the dashboard's [InsightsUiState] and exposes
// `loadCached()` / `dataChanged()` / `dismiss()`.
//
// THE MAKE-OR-BREAK (SC2 / PITFALLS "Insights recompute on main thread"): `recompute()` reads the value
// snapshots + dismissed set, then runs the PURE `InsightsEngine` INSIDE `withContext(defaultDispatcher)`
// — the engine call is the ONLY thing off the main/Compose thread. The result is published back through
// the [StateFlow]. Rapid edits (save / foreground / dismiss) are coalesced by a cancellable debounce
// (`delay(debounceMillis)`, D-01). An input FINGERPRINT drives cache HIT (unchanged → skip the engine)
// vs MISS (recompute + `persist` replace-all), so a cold open loads the cache instantly (D-04) and only
// recomputes when inputs actually changed.
//
// INTERFACE-FIRST (STATE `[08-02]` seam): EVERY dependency is an injected closure so unit tests need no
// store, no clock, and no real time — a call-counting `engine`, a driveable dispatcher, explicit
// load/persist closures. `AppDependencies` wires the real `EndoRepository` seams + the default engine.
//
// SEPARATION (CONTEXT §5): this file imports NO notification / alarm / WorkManager API — the reminders
// package stays the SOLE notification owner (NotificationSeparationTest keeps this honest).
//
// PRIVACY (T-22-01): only value snapshots + dismissed keys cross into the engine; only the gated
// `Insight` is persisted; nothing here logs snapshots, keys, or insight copy.
package com.endoapp.app.insights

import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightsEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** The immutable UI state the Phase-22 dashboard renders (D-13): the published gated insights + an
 *  "updating" affordance while an off-main recompute is in flight. */
data class InsightsUiState(
    val insights: List<Insight> = emptyList(),
    val isUpdating: Boolean = false,
)

/**
 * The app-scoped insights coordinator (D-13). Built once in `AppDependencies` over the live repository
 * seams; Plan 04 threads its [state] into the Insights tab.
 *
 * @param loadSnapshots     read the per-day value snapshots (repo.daySnapshots()).
 * @param loadDismissed     read the dismissed signalKeys (repo.dismissedSignalKeys()).
 * @param loadCache         load the durable cache instantly on open (repo.loadCachedInsights()).
 * @param persist           replace-all the cache (repo.replaceCachedInsights(...)) — best-effort.
 * @param writeDismissed    append a DismissedInsight (repo.dismissInsight(...)) — NON-destructive.
 * @param now               the injected clock day (the engine's `asOf` reference).
 * @param nowMillis         the injected wall-clock millis (the cache `computedAt` stamp).
 * @param engine            the PURE engine seam; defaults to the real [InsightsEngine].
 * @param scope             the app-lifetime scope the coordinator launches on.
 * @param defaultDispatcher the OFF-MAIN dispatcher the engine runs inside (default [Dispatchers.Default]).
 * @param debounceMillis    the quiet period that coalesces rapid triggers into ONE recompute (D-01).
 */
class InsightsCoordinator(
    private val loadSnapshots: suspend () -> List<DaySnapshot>,
    private val loadDismissed: suspend () -> Set<String>,
    private val loadCache: suspend () -> List<Insight>,
    private val persist: suspend (List<Insight>, Long, String) -> Unit,
    private val writeDismissed: suspend (String) -> Unit,
    private val now: () -> LocalDate,
    private val nowMillis: () -> Long,
    private val scope: CoroutineScope,
    private val engine: (List<DaySnapshot>, LocalDate, Set<String>) -> List<Insight> = InsightsEngine::insights,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val debounceMillis: Long = 400,
) {

    private val _state = MutableStateFlow(InsightsUiState())

    /** The gated insights + updating flag the dashboard observes (published on the coordinator's scope). */
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    /** The fingerprint of the inputs the last recompute ran on. An unchanged fingerprint ⇒ cache HIT. */
    private var lastFingerprint: String? = null

    /** The cancellable debounce job; a new [dataChanged] cancels and reschedules it (coalesce, D-01). */
    private var debounceJob: Job? = null

    /**
     * Paint the cached insights INSTANTLY (D-04) — no engine run, no off-main hop. The dashboard calls
     * this on appear so the screen paints immediately; it then drives an initial [dataChanged]. Never
     * blocks (launched on [scope]).
     */
    fun loadCached() {
        scope.launch {
            val cached = loadCache()
            _state.update { it.copy(insights = cached) }
        }
    }

    /**
     * Signal that the underlying data changed (a save, a dismiss, or a foreground). Coalesces rapid
     * calls into ONE recompute after a quiet period (D-01): cancel-and-reschedule the debounce.
     */
    fun dataChanged() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMillis)
            recompute()
        }
    }

    /**
     * Dismiss an insight (D-09/D-10/T-22-01b): append a NON-destructive [com.endoapp.app.data.entities.DismissedInsight]
     * (NEVER deletes logged data), optimistically drop the card from the published state for an instant
     * response, then trigger a recompute that re-reads the dismissed set and excludes the signal for good.
     */
    fun dismiss(signalKey: String) {
        scope.launch {
            writeDismissed(signalKey)
            _state.update { s -> s.copy(insights = s.insights.filter { it.signalKey != signalKey }) }
            dataChanged()
        }
    }

    /**
     * Recompute: read inputs, compute the fingerprint, SKIP on a cache hit, else run the PURE engine OFF
     * the main thread ([withContext] on [defaultDispatcher] — the ONLY off-main work) and publish +
     * persist. Private: the only way to drive it is [dataChanged] (so every recompute is debounced).
     */
    private suspend fun recompute() {
        val snapshots = loadSnapshots()
        val dismissed = loadDismissed()
        val fingerprint = InsightFingerprint.make(snapshots, dismissed)

        // Cache HIT: inputs identical to the last recompute → skip the engine entirely (D-04).
        if (fingerprint == lastFingerprint) {
            _state.update { it.copy(isUpdating = false) }
            return
        }

        _state.update { it.copy(isUpdating = true) }
        // OFF-MAIN (SC2 / PITFALLS): the pure engine runs on the default dispatcher — NEVER on the
        // main/Compose thread. Snapshots + dismissed are value types; nothing else crosses.
        val result = withContext(defaultDispatcher) { engine(snapshots, now(), dismissed) }

        // Persist is best-effort: a cache-write failure must never drop the fresh insights or crash.
        runCatching { persist(result, nowMillis(), fingerprint) }
        lastFingerprint = fingerprint
        _state.update { it.copy(insights = result, isUpdating = false) }
    }
}
