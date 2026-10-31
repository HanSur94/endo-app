package com.endoapp.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.data.entities.CachedInsight
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightConfidence
import com.endoapp.insights.InsightKind
import com.endoapp.insights.InsightPoint
import com.endoapp.insights.TrendWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Runnable JVM proof (Robolectric + real in-memory Room, the ImportedSampleDaoTest lane) for the
 * Phase-22 consent-gated insight-cache seam the [com.endoapp.app.insights.InsightsCoordinator] drives:
 *
 *  1. `replaceCachedInsights` / `loadCachedInsights` round-trip a `List<Insight>` through Room — the
 *     decoded list is STRUCTURALLY EQUAL to the encoded input (ids preserved), and it is REPLACE-ALL
 *     (a second replace does not accrete rows).
 *  2. A corrupt / null payload row is SKIPPED on load — never fatal (T-08-04 resilient-decode posture).
 *  3. PRIVACY (T-22-01): the stored payload carries ONLY the gated `Insight` — NO `DaySnapshot`/note
 *     field ever crosses into the cache.
 *  4. All three new methods FAIL CLOSED pre-consent (throw under Unknown AND NotConsented), like the
 *     sibling gated methods.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryInsightCacheTest {

    private lateinit var db: EndoDatabase

    private class SettableConsentStore(state: ConsentState) : ConsentStore {
        private val _state = MutableStateFlow(state)
        override val state: StateFlow<ConsentState> = _state
        override suspend fun grant() { _state.value = ConsentState.Consented(0L) }
        override suspend fun withdraw() { _state.value = ConsentState.NotConsented }
    }

    private fun repo(state: ConsentState = ConsentState.Consented(0L)): EndoRepository =
        EndoRepository.from(SettableConsentStore(state), db)

    private fun insight(signalKey: String, severity: Double): Insight = Insight.derived(
        kind = InsightKind.TREND,
        window = TrendWindow.LAST7,
        signalKey = signalKey,
        headline = "A descriptive headline",
        detail = "A descriptive, non-causal detail line.",
        confidence = InsightConfidence.MEDIUM,
        points = listOf(InsightPoint(date = LocalDate.ofEpochDay(100), value = severity)),
        sampleSize = 7,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EndoDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun replaceAll_roundTripsInsights_preservingIds() = runBlocking {
        val repo = repo()
        val original = listOf(
            insight("symptom.pelvicPain.severity", 3.0),
            insight("factor.sleep.hours", 6.5),
        )

        repo.replaceCachedInsights(original, computedAtMillis = 111L, fingerprint = "fp-1")
        val loaded = repo.loadCachedInsights()

        assertEquals("decode == encode input (structural equality, ids preserved)", original, loaded)
        assertEquals("both derived ids survive the round-trip", original.map { it.id }, loaded.map { it.id })
    }

    @Test
    fun replaceAll_isWholesale_notAccretive() = runBlocking {
        val repo = repo()
        repo.replaceCachedInsights(
            listOf(insight("a", 1.0), insight("b", 2.0)),
            computedAtMillis = 1L, fingerprint = "fp-a",
        )
        // A second replace with a single insight must leave EXACTLY one row (clear-then-insert).
        repo.replaceCachedInsights(listOf(insight("c", 3.0)), computedAtMillis = 2L, fingerprint = "fp-b")

        val loaded = repo.loadCachedInsights()
        assertEquals("replace-all leaves only the latest set", listOf(insight("c", 3.0)), loaded)
    }

    @Test
    fun corruptPayloadRow_isSkipped_notFatal() = runBlocking {
        val repo = repo()
        // One valid insight, persisted through the gated path.
        repo.replaceCachedInsights(listOf(insight("valid.signal", 4.0)), computedAtMillis = 1L, fingerprint = "fp")
        // Inject a corrupt row DIRECTLY on the DAO (bypassing the encode) to simulate a damaged blob.
        db.insightsDao().insertCached(
            listOf(
                CachedInsight(
                    id = "corrupt-row",
                    signalKey = "corrupt.signal",
                    kindRaw = "trend",
                    windowRaw = "last7",
                    payload = "{ this is not valid insight json",
                    computedAtMillis = 2L,
                    fingerprint = "fp",
                ),
            ),
        )

        val loaded = repo.loadCachedInsights()
        assertEquals("the corrupt row is skipped, the valid insight survives", 1, loaded.size)
        assertEquals("valid.signal", loaded.single().signalKey)
    }

    @Test
    fun nullPayloadRow_isSkipped_notFatal() = runBlocking {
        val repo = repo()
        db.insightsDao().insertCached(
            listOf(CachedInsight(id = "no-payload", payload = null, computedAtMillis = 1L)),
        )
        assertEquals("a null-payload row decodes to nothing, not a crash", emptyList<Insight>(), repo.loadCachedInsights())
    }

    @Test
    fun storedPayload_carriesOnlyTheInsight_noSnapshotOrNoteField_T2201() = runBlocking {
        val repo = repo()
        repo.replaceCachedInsights(listOf(insight("symptom.pelvicPain.severity", 3.0)), computedAtMillis = 1L, fingerprint = "fp")

        val payload = db.insightsDao().allCached().single().payload ?: error("payload must be present")
        // The gated Insight's JSON must NOT carry any DaySnapshot-only field — the privacy boundary.
        listOf("hasNote", "suppressed", "activityMinutes", "sleepHours", "foodTags", "medicationsTaken").forEach { field ->
            assertTrue(
                "T-22-01: the cache payload must never carry the DaySnapshot field '$field' — only the gated Insight is serialized.",
                !payload.contains(field),
            )
        }
        // Sanity: it IS a real Insight payload (carries the signalKey it was built from).
        assertTrue("payload is the serialized Insight", payload.contains("symptom.pelvicPain.severity"))
    }

    @Test
    fun allThreeNewMethods_failClosed_preConsent() {
        listOf(ConsentState.Unknown, ConsentState.NotConsented).forEach { state ->
            val repo = repo(state)
            assertThrows("loadCachedInsights must throw under $state", IllegalStateException::class.java) {
                runBlocking { repo.loadCachedInsights() }
            }
            assertThrows("replaceCachedInsights must throw under $state", IllegalStateException::class.java) {
                runBlocking { repo.replaceCachedInsights(emptyList(), 0L, "fp") }
            }
            assertThrows("dismissedSignalKeys must throw under $state", IllegalStateException::class.java) {
                runBlocking { repo.dismissedSignalKeys() }
            }
        }
    }
}
