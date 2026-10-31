package com.endoapp.app.ui.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.BodyRegion
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.FoodTag
import com.endoapp.insights.SleepQuality
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

/**
 * The end-to-end honesty proof (ATRK-05 / threat T-20-01): a [DayDraft] written through the
 * consent-gated [EndoRepository] into an in-memory Room DB, reloaded per-day, and reconstructed by
 * [DayDraftMapper.fromRows] with the nil-vs-0 distinction intact. Mirrors `DeleteDayCascadeTest`'s
 * in-memory Room + `ApplicationProvider` harness (emulator lane; compiles on the JVM).
 */
@RunWith(AndroidJUnit4::class)
class DayLogRoundTripTest {

    private lateinit var db: EndoDatabase
    private lateinit var repo: EndoRepository

    private val day = 20_200L
    // Stable, unique id per row key (deterministic per test run — upserts stay idempotent within a day).
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

    /** Persist a draft's rows through the repository, then reload the day into a fresh draft. */
    private fun persistAndReload(draft: DayDraft): DayDraft = runBlocking {
        val rows = DayDraftMapper.toRows(day, draft, idFor)
        repo.upsertDayEntry(rows.dayEntry)
        rows.symptoms.forEach { repo.upsertSymptom(it) }
        rows.pain.forEach { repo.upsertPain(it) }
        rows.bleeding.forEach { repo.upsertBleeding(it) }
        rows.factors.forEach { repo.upsertFactor(it) }

        val reloaded = DayLogRows(
            dayEntry = repo.dayEntriesForDay(day).first(),
            symptoms = repo.symptomsForDay(day),
            pain = repo.painForDay(day),
            bleeding = repo.bleedingForDay(day),
            factors = repo.factorsForDay(day),
        )
        DayDraftMapper.fromRows(reloaded)
    }

    @Test
    fun mixedDraft_preservesNilVsZero_throughRepository() {
        val draft = DayDraft(
            symptoms = mapOf(
                SymptomType.PELVIC_PAIN to null, // selected, severity unspecified
                SymptomType.FATIGUE to 0,        // a real logged 0
                SymptomType.HEADACHE to 6,
            ),
            pain = mapOf(BodyRegion.LOWER_BACK to 0, BodyRegion.HIPS to null),
            flow = FlowIntensity.LIGHT,
            clots = false,
            foodTags = setOf(FoodTag.CAFFEINE),
            foodNote = "coffee",
            activityType = ActivityType.WALKING,
            activityMinutes = 0.0, // 0 minutes WITH a type = real 0
            activityIntensity = ActivityIntensity.LOW,
            sleepHours = 0.0,
            sleepHoursSet = true, // 0.0h with hoursSet = real 0
            sleepQuality = SleepQuality.POOR,
            medsTaken = setOf("med-1"),
            tookPRN = true,
            stress = 0, // opted in, a real 0
            mood = 2,
            note = "logged some, skipped some",
        )

        val back = persistAndReload(draft)

        // Real zeros survive as real zeros.
        assertEquals(0, back.symptoms[SymptomType.FATIGUE])
        assertEquals(0, back.pain[BodyRegion.LOWER_BACK])
        assertEquals(0.0, back.activityMinutes!!, 0.0)
        assertTrue(back.sleepHoursSet)
        assertEquals(0.0, back.sleepHours!!, 0.0)
        assertEquals(0, back.stress)

        // Nils survive as nils (never coerced to 0).
        assertTrue(back.symptoms.containsKey(SymptomType.PELVIC_PAIN))
        assertNull(back.symptoms[SymptomType.PELVIC_PAIN])
        assertNull(back.pain[BodyRegion.HIPS])

        // The whole draft round-trips to identity.
        assertEquals(draft, back)
    }

    @Test
    fun emptyConfirm_persistsOnlyTheDayEntryMarker() = runBlocking {
        val rows = DayDraftMapper.toRows(day, DayDraft(), idFor)
        repo.upsertDayEntry(rows.dayEntry)
        // No symptom/pain/bleeding/factor writes — an empty confirm records only the anchor.

        assertEquals(1, repo.dayEntriesForDay(day).size)
        assertTrue(repo.symptomsForDay(day).isEmpty())
        assertTrue(repo.painForDay(day).isEmpty())
        assertTrue(repo.bleedingForDay(day).isEmpty())
        assertTrue(repo.factorsForDay(day).isEmpty())

        val back = DayDraftMapper.fromRows(
            DayLogRows(repo.dayEntriesForDay(day).first(), emptyList(), emptyList(), emptyList(), emptyList()),
        )
        assertEquals(DayDraft(), back)
        assertFalse(back.tookPRN)
    }
}
