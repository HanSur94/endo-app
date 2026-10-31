package com.endoapp.app.insights

import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightConfidence
import com.endoapp.insights.InsightKind
import com.endoapp.insights.InsightPoint
import com.endoapp.insights.TrendWindow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Pure `runTest` proof (virtual time, a counting engine seam, a driveable dispatcher — no store, no
 * clock, no real time) that [InsightsCoordinator] behaves per D-01/D-04/D-09/D-13 + T-22-01:
 *
 *  1. `loadCached()` paints from the cache BEFORE any engine run (instant open).
 *  2. A cache MISS runs the engine ONCE and persists; a cache HIT (unchanged fingerprint) SKIPS it.
 *  3. Three rapid `dataChanged()` calls coalesce to ONE recompute (debounce N→1, D-01).
 *  4. `dismiss(key)` WRITES an append-only DismissedInsight (never a delete) AND feeds `key` into the
 *     engine's `dismissed` set on the next recompute (T-22-01b).
 *  5. The engine runs OFF the main thread — inside `withContext(defaultDispatcher)` (SC2 / T-22-01c).
 */
class InsightsCoordinatorTest {

    /** A call-counting engine seam that records its inputs. */
    private class CountingEngine(
        private val output: List<Insight> = emptyList(),
    ) : (List<DaySnapshot>, LocalDate, Set<String>) -> List<Insight> {
        var calls = 0
            private set
        var lastDismissed: Set<String> = emptySet()
            private set

        override fun invoke(snapshots: List<DaySnapshot>, asOf: LocalDate, dismissed: Set<String>): List<Insight> {
            calls++
            lastDismissed = dismissed
            return output
        }
    }

    private fun snapshot(epochDay: Long): DaySnapshot = DaySnapshot.of(day = LocalDate.ofEpochDay(epochDay))

    private fun insight(signalKey: String): Insight = Insight.derived(
        kind = InsightKind.TREND,
        window = TrendWindow.LAST7,
        signalKey = signalKey,
        headline = "Headline",
        detail = "Detail",
        confidence = InsightConfidence.MEDIUM,
        points = listOf(InsightPoint(date = LocalDate.ofEpochDay(100), value = 3.0)),
        sampleSize = 7,
    )

    /** Build a coordinator whose scope + off-main dispatcher share the runTest scheduler (deterministic). */
    private fun coordinator(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        engine: CountingEngine,
        cache: List<Insight> = emptyList(),
        loadSnapshots: suspend () -> List<DaySnapshot> = { listOf(snapshot(100)) },
        loadDismissed: suspend () -> Set<String> = { emptySet() },
        persist: suspend (List<Insight>, Long, String) -> Unit = { _, _, _ -> },
        writeDismissed: suspend (String) -> Unit = {},
        debounceMillis: Long = 400,
    ): InsightsCoordinator = InsightsCoordinator(
        loadSnapshots = loadSnapshots,
        loadDismissed = loadDismissed,
        loadCache = { cache },
        persist = persist,
        writeDismissed = writeDismissed,
        now = { LocalDate.ofEpochDay(200) },
        nowMillis = { 0L },
        scope = scope,
        engine = engine,
        defaultDispatcher = dispatcher,
        debounceMillis = debounceMillis,
    )

    @Test
    fun loadCached_paintsCache_withoutRunningEngine() = runTest {
        val engine = CountingEngine()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val c = coordinator(this, dispatcher, engine, cache = listOf(insight("cached")))

        c.loadCached()
        advanceUntilIdle()

        assertEquals("the cache is painted instantly", listOf(insight("cached")), c.state.value.insights)
        assertEquals("loadCached() never runs the engine", 0, engine.calls)
    }

    @Test
    fun cacheMiss_recomputesOnce_andPersists() = runTest {
        val engine = CountingEngine(output = listOf(insight("fresh")))
        val dispatcher = StandardTestDispatcher(testScheduler)
        var persistCount = 0
        var persistedFingerprint: String? = null
        val c = coordinator(
            this, dispatcher, engine,
            persist = { _, _, fp -> persistCount++; persistedFingerprint = fp },
        )

        c.dataChanged()
        advanceUntilIdle()

        assertEquals("a cache MISS runs the engine exactly once", 1, engine.calls)
        assertEquals("a MISS persists the fresh cache once", 1, persistCount)
        assertTrue("the persisted fingerprint is non-empty", !persistedFingerprint.isNullOrEmpty())
        assertEquals("the fresh insights are published", listOf(insight("fresh")), c.state.value.insights)
        assertFalse("isUpdating is cleared when the recompute finishes", c.state.value.isUpdating)
    }

    @Test
    fun cacheHit_unchangedFingerprint_skipsEngine() = runTest {
        val engine = CountingEngine()
        val dispatcher = StandardTestDispatcher(testScheduler)
        // Constant snapshots + dismissed ⇒ the 2nd recompute sees an unchanged fingerprint.
        val c = coordinator(this, dispatcher, engine)

        c.dataChanged()
        advanceUntilIdle()
        assertEquals("first recompute is a MISS", 1, engine.calls)

        c.dataChanged()
        advanceUntilIdle()
        assertEquals("unchanged inputs are a cache HIT — the engine is skipped", 1, engine.calls)
    }

    @Test
    fun rapidDataChanged_coalesceToOneRecompute() = runTest {
        val engine = CountingEngine()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val c = coordinator(this, dispatcher, engine, debounceMillis = 400)

        // Three rapid triggers within the debounce window — each cancels the previous.
        c.dataChanged()
        c.dataChanged()
        c.dataChanged()
        advanceUntilIdle()

        assertEquals("three rapid triggers coalesce to ONE engine run (debounce N→1)", 1, engine.calls)
    }

    @Test
    fun dismiss_writesAppendOnlyDismissal_andExcludesKeyOnRecompute() = runTest {
        val engine = CountingEngine(output = listOf(insight("keyB")))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val written = mutableListOf<String>()
        var dismissedSet = emptySet<String>()
        val c = coordinator(
            this, dispatcher, engine,
            loadDismissed = { dismissedSet },
            // A dismiss APPENDS to the dismissed set — it never deletes a logged row.
            writeDismissed = { key -> written.add(key); dismissedSet = dismissedSet + key },
        )

        c.dismiss("keyA")
        advanceUntilIdle()

        assertEquals("dismiss writes exactly one append-only DismissedInsight (never a delete)", listOf("keyA"), written)
        assertTrue("the dismissed key is fed into the engine's dismissed set on recompute", engine.lastDismissed.contains("keyA"))
        assertFalse("the dismissed signal is not in the recomputed insights", c.state.value.insights.any { it.signalKey == "keyA" })
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun engineRunsOffTheMainThread_onTheInjectedDispatcher() {
        // A real, distinctly-named off-main dispatcher + a real app scope: the engine seam records the
        // thread it runs on. If the engine ran on the caller thread the name would NOT match — proving
        // the engine executes only inside withContext(defaultDispatcher) (SC2 / T-22-01c).
        val engineThread = AtomicReference<String>()
        val latch = CountDownLatch(1)
        val offMain = newSingleThreadContext("insights-engine-off-main")
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val c = InsightsCoordinator(
                loadSnapshots = { listOf(snapshot(100)) },
                loadDismissed = { emptySet() },
                loadCache = { emptyList() },
                persist = { _, _, _ -> },
                writeDismissed = {},
                now = { LocalDate.ofEpochDay(200) },
                nowMillis = { 0L },
                scope = scope,
                engine = { _, _, _ -> engineThread.set(Thread.currentThread().name); latch.countDown(); emptyList() },
                defaultDispatcher = offMain,
                debounceMillis = 0,
            )

            c.dataChanged()
            assertTrue("the engine ran within 5s", latch.await(5, TimeUnit.SECONDS))
            assertTrue(
                "the engine ran on the injected off-main dispatcher, not the caller thread (was ${engineThread.get()})",
                engineThread.get().startsWith("insights-engine-off-main"),
            )
        } finally {
            scope.cancel()
            offMain.close()
        }
    }
}
