package com.endoapp.app.ui.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.designsystem.BloomTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Medication add + SOFT-archive (ATRK-03 / ATRK-05, threat T-20-05), proven RUNNABLY on the JVM
 * (Robolectric + real in-memory Room) through the consent-gated [EndoRepository], plus the
 * [AddMedicationSheet] Save-gating behavior on the Compose lane:
 *
 * 1. Adding a med persists it and it appears in the active list (`observeMedications`).
 * 2. Archiving hides it from the active list while the definition SURVIVES in `allMedications` — so a
 *    day that logged it as taken (keyed by the med id) keeps its meaning; there is NO hard-delete path.
 * 3. The add sheet's Save is disabled for a blank name and enabled once a name is entered; Save hands
 *    up a Medication carrying the entered name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: EndoDatabase
    private lateinit var repo: EndoRepository

    private class ConsentedStore : ConsentStore {
        override val state: StateFlow<ConsentState> =
            MutableStateFlow(ConsentState.Consented(grantedAtMillis = 0L))
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
    fun add_persistsMedication_appearsInActiveList() = runBlocking {
        val med = Medication(id = "m1", name = "Ibuprofen", note = "400mg", createdAtMillis = 1_000L)
        repo.upsertMedication(med)

        val active = repo.observeMedications().first()
        assertEquals("the added med is in the active list", listOf("Ibuprofen"), active.map { it.name })
    }

    @Test
    fun archive_softHidesFromActive_butDefinitionSurvives() = runBlocking {
        val med = Medication(id = "m1", name = "Ibuprofen", createdAtMillis = 1_000L)
        repo.upsertMedication(med)
        assertEquals(1, repo.observeMedications().first().size)

        // Soft-archive — never a hard delete.
        repo.upsertMedication(med.copy(isArchived = true))

        // Hidden from the active saved list…
        assertTrue("archived med leaves the active list", repo.observeMedications().first().isEmpty())
        // …but the DEFINITION survives, so prior 'taken' history keyed by id stays valid.
        val all = repo.allMedications()
        assertEquals("the definition still exists", 1, all.size)
        assertEquals("m1", all.single().id)
        assertEquals("archived flag is set", true, all.single().isArchived)
    }

    @Test
    fun addSheet_saveDisabledUntilNameNonBlank_thenReturnsMedication() {
        var saved: Medication? = null
        composeRule.setContent {
            BloomTheme {
                AddMedicationSheet(
                    onSave = { saved = it },
                    onDismiss = {},
                    now = 5_000L,
                    idFor = { "m-new" },
                )
            }
        }

        // Blank name → Save disabled.
        composeRule.onNodeWithTag("settings.addMed.save").assertIsNotEnabled()

        composeRule.onNodeWithTag("settings.addMed.name").performTextInput("Naproxen")
        composeRule.waitForIdle()

        // Non-blank name → Save enabled; tapping it hands up the new definition.
        composeRule.onNodeWithTag("settings.addMed.save").assertIsEnabled()
        composeRule.onNodeWithTag("settings.addMed.save").performClick()
        composeRule.waitForIdle()

        val med = requireNotNull(saved) { "Save must hand up a Medication" }
        assertEquals("Naproxen", med.name)
        assertEquals("m-new", med.id)
        assertNull("a fresh definition is active (isArchived null)", med.isArchived)
        assertNotNull(med.createdAtMillis)
    }
}
