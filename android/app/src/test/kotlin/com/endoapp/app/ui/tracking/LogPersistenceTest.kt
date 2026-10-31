package com.endoapp.app.ui.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.insights.BodyRegion
import com.endoapp.insights.SymptomType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The composition-root persistence wiring, proven RUNNABLY on the JVM (Robolectric + real in-memory
 * Room) — the seams [AppGate][com.endoapp.app.AppGate] binds so the running app ACTUALLY persists and
 * reloads a logged day (closing the Plans 20-01/02 deferral, threat T-20-01):
 *
 * 1. [LogPersistence.upsertDay] → [LogPersistence.loadDay] round-trips a mixed draft through the
 *    consent-gated [EndoRepository] with nil-vs-0 intact (a slid-to-0 severity stays a real 0; an
 *    untouched field stays nil).
 * 2. [LogPersistence.replaceDay] honestly DROPS a deselected field on the next reload (the Day Log
 *    editor's authoritative-edit semantic) — no stale rows survive an edit.
 * 3. [LogPersistence.loadDay] returns `null` for a never-logged day (the fresh-day signal).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogPersistenceTest {

    private lateinit var db: EndoDatabase
    private lateinit var repo: EndoRepository

    private val day = 20_300L
    private val idFor: (String) -> String = { key -> "$day:$key" }

    private class ConsentedStore : ConsentStore {
        override val state: StateFlow<ConsentState> = MutableStateFlow(ConsentState.Consented(grantedAtMillis = 0L))
        override suspend fun grant() = Unit
        override suspend fun withdraw() = Unit
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EndoDatabase::class.java,
        ).build()
        repo = EndoRepository.from(ConsentedStore(), db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsertThenLoad_roundTripsNilVsZero_throughRealRoom() = runBlocking {
        val draft = DayDraft(
            symptoms = mapOf(
                SymptomType.PELVIC_PAIN to null, // selected, severity unspecified (nil)
                SymptomType.FATIGUE to 0,        // a real logged 0
            ),
            pain = mapOf(BodyRegion.LOWER_BACK to 0), // a real 0 on the body map
            note = "logged some, skipped some",
        )

        LogPersistence.upsertDay(repo, DayDraftMapper.toRows(day, draft, idFor))
        val back = requireNotNull(LogPersistence.loadDay(repo, day))

        assertEquals("real 0 severity survives", 0, back.symptoms[SymptomType.FATIGUE])
        assertEquals("real 0 pain survives", 0, back.pain[BodyRegion.LOWER_BACK])
        assertTrue("nil-severity symptom is present", back.symptoms.containsKey(SymptomType.PELVIC_PAIN))
        assertNull("nil severity stays nil (never coerced to 0)", back.symptoms[SymptomType.PELVIC_PAIN])
        assertEquals(draft, back)
    }

    @Test
    fun replaceDay_dropsDeselectedRows_onReload() = runBlocking {
        // First persist a day with TWO symptoms.
        val first = DayDraft(symptoms = mapOf(SymptomType.PELVIC_PAIN to 5, SymptomType.FATIGUE to 3))
        LogPersistence.upsertDay(repo, DayDraftMapper.toRows(day, first, idFor))
        assertEquals(2, LogPersistence.loadDay(repo, day)!!.symptoms.size)

        // Edit the day: drop FATIGUE, keep PELVIC_PAIN — the editor REPLACES the whole day.
        val edited = DayDraft(symptoms = mapOf(SymptomType.PELVIC_PAIN to 5))
        LogPersistence.replaceDay(repo, DayDraftMapper.toRows(day, edited, idFor))

        val back = requireNotNull(LogPersistence.loadDay(repo, day))
        assertEquals("the deselected symptom is gone (honest edit)", edited, back)
        assertFalse("no stale FATIGUE row survives", back.symptoms.containsKey(SymptomType.FATIGUE))
    }

    @Test
    fun loadDay_returnsNull_forNeverLoggedDay() = runBlocking {
        assertNull(LogPersistence.loadDay(repo, day))
    }
}
