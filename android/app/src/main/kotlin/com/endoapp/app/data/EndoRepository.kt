package com.endoapp.app.data

import com.endoapp.app.data.daos.DayEntryDao
import com.endoapp.app.data.daos.DeleteDayDao
import com.endoapp.app.data.daos.InsightsDao
import com.endoapp.app.data.daos.MedicationDao
import com.endoapp.app.data.daos.SymptomDao
import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.CachedInsight
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.DismissedInsight
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.app.data.entities.TreatmentState
import com.endoapp.app.insightsbridge.SnapshotProjector
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.Insight
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * The consent-gated facade over the health data store — the ONLY path through which health data is
 * read or written (ONBD-01, APER-01/02).
 *
 * ## Consent by construction (T-18-02)
 * [checkConsented] is the FIRST statement of EVERY health read/write. It throws
 * [IllegalStateException] whenever [ConsentStore.state] is not [ConsentState.Consented] — so
 * [ConsentState.Unknown] (the async-DataStore startup gap) AND [ConsentState.NotConsented] both fail
 * closed. This is a HARD throw, not `assert`: Android has no debug-only assert that survives R8, so
 * the iOS `assertConsented()` (a debug `assert`) becomes a real throw here.
 *
 * ## Atomic delete-day
 * [deleteDay] gates, then delegates to the single-[androidx.room.Transaction] [DeleteDayDao.deleteDay]
 * — never N sequential deletes of its own, so a crash mid-cascade cannot orphan rows.
 *
 * `daySnapshots()` (which needs the SnapshotProjector) deliberately lands in Plan 04; this facade
 * ships the consent gate + the per-aggregate write/read/delete surface this phase needs.
 */
class EndoRepository(
    private val consent: ConsentStore,
    private val dayEntryDao: DayEntryDao,
    private val symptomDao: SymptomDao,
    private val medicationDao: MedicationDao,
    private val insightsDao: InsightsDao,
    private val deleteDayDao: DeleteDayDao,
) {

    /**
     * The consent gate. Called first by every health method; throws pre-consent so no health data
     * can be read or written before explicit, current consent. [ConsentState.Unknown] counts as not
     * consented (fail closed).
     */
    private fun checkConsented() {
        if (consent.state.value !is ConsentState.Consented) {
            throw IllegalStateException("Repository accessed before consent (ONBD-01).")
        }
    }

    // --- Writes (each gated) ---

    suspend fun upsertDayEntry(entry: DayEntry) {
        checkConsented()
        dayEntryDao.upsert(entry)
    }

    suspend fun upsertSymptom(reading: SymptomReading) {
        checkConsented()
        symptomDao.upsertSymptom(reading)
    }

    suspend fun upsertPain(point: PainPoint) {
        checkConsented()
        symptomDao.upsertPain(point)
    }

    suspend fun upsertBleeding(event: BleedingEvent) {
        checkConsented()
        symptomDao.upsertBleeding(event)
    }

    suspend fun upsertFactor(reading: FactorReading) {
        checkConsented()
        symptomDao.upsertFactor(reading)
    }

    suspend fun upsertImported(sample: ImportedSample) {
        checkConsented()
        symptomDao.upsertImported(sample)
    }

    /**
     * Propagate a Health Connect deletion (T-21-02 / T-21-02d): gates FIRST (throws under Unknown /
     * NotConsented, the same fail-closed posture as [upsertImported]), then removes the imported row
     * whose `hcRecordId` matches. The single consent-gated deletion path the import coordinator drives.
     */
    suspend fun deleteImportedByHcRecordId(hcRecordId: String) {
        checkConsented()
        symptomDao.deleteImportedByHcRecordId(hcRecordId)
    }

    suspend fun upsertMedication(medication: Medication) {
        checkConsented()
        // Length-bound the free-text name/note HERE (the seam), never in the UI (UI-SPEC §Section-row
        // contracts): the store is the single guard, so a pasted essay can never bloat a definition row.
        medicationDao.upsertMedication(
            medication.copy(
                name = medication.name?.take(MAX_MED_TEXT),
                note = medication.note?.take(MAX_MED_TEXT),
            ),
        )
    }

    suspend fun upsertTreatment(state: TreatmentState) {
        checkConsented()
        medicationDao.upsertTreatment(state)
    }

    suspend fun dismissInsight(dismissed: DismissedInsight) {
        checkConsented()
        insightsDao.insertDismissed(dismissed)
    }

    /**
     * The live day history (newest first) the Log-home renders. NOT method-gated (a cold [Flow] gate
     * cannot fail-closed synchronously) — instead it is safe by CONSTRUCTION: it is wired only inside
     * [com.endoapp.app.AppGate]'s `Consented` arm, so it is never collected pre-consent (mirrors the
     * `recentDays` consent-by-construction posture the shell already relies on).
     */
    fun observeDayEntries(): Flow<List<DayEntry>> = dayEntryDao.observeAll()

    /**
     * The live saved-medication list (active / non-archived, newest first) the Settings manage-meds
     * screen renders. Like [observeDayEntries] it is NOT method-gated (a cold [Flow] cannot fail-closed
     * synchronously) — it is safe by CONSTRUCTION: wired only inside [com.endoapp.app.AppGate]'s
     * `Consented` arm, so it is never collected pre-consent.
     */
    fun observeMedications(): Flow<List<Medication>> = medicationDao.observeActive()

    // --- Per-day reads (each gated) ---

    suspend fun dayEntriesForDay(epochDay: Long): List<DayEntry> {
        checkConsented()
        return dayEntryDao.forDay(epochDay)
    }

    suspend fun symptomsForDay(epochDay: Long): List<SymptomReading> {
        checkConsented()
        return symptomDao.symptomsForDay(epochDay)
    }

    suspend fun painForDay(epochDay: Long): List<PainPoint> {
        checkConsented()
        return symptomDao.painForDay(epochDay)
    }

    suspend fun bleedingForDay(epochDay: Long): List<BleedingEvent> {
        checkConsented()
        return symptomDao.bleedingForDay(epochDay)
    }

    suspend fun factorsForDay(epochDay: Long): List<FactorReading> {
        checkConsented()
        return symptomDao.factorsForDay(epochDay)
    }

    suspend fun importedForDay(epochDay: Long): List<ImportedSample> {
        checkConsented()
        return symptomDao.importedForDay(epochDay)
    }

    /**
     * `true` iff at least one Health Connect [ImportedSample] row exists (gated). Drives the honest
     * connect/import "you have imported data" vs empty-state decision (AHC-03) — read from the store, so
     * a differential refresh that imports no NEW rows still reports the data already present rather than
     * a dishonest empty. Never fabricates: no imported row ⇒ `false`.
     */
    suspend fun hasImportedData(): Boolean {
        checkConsented()
        return symptomDao.allImported().isNotEmpty()
    }

    /** Every medication definition, INCLUDING soft-archived ones (gated) — the archive round-trip
     *  proof: a med hidden from [observeMedications] still lives here, so prior 'taken' history keyed by
     *  its id stays valid. */
    suspend fun allMedications(): List<Medication> {
        checkConsented()
        return medicationDao.allMedications()
    }

    /** The current standing treatment/suppression state (the latest-started row), or null (gated). Feeds
     *  the Settings cycle-tracking-off toggle's initial value (`modeRaw == "suppressed"`). */
    suspend fun latestTreatment(): TreatmentState? {
        checkConsented()
        return medicationDao.latestTreatment()
    }

    // --- Projection read (gated; the single path onto the pure SnapshotProjector) ---

    /**
     * The consent-gated projection read (APER-05) — the ONE read path the Phase-22 dashboard / report
     * consumes. Gates FIRST, then fetches every day-scoped row list, derives `suppressed` from the
     * latest [TreatmentState] (`modeRaw == "suppressed"`), and delegates to the pure
     * [SnapshotProjector.project]. A one-shot suspend read returning stable value lists for the engine;
     * Room entities never escape this method (only `:insights` [DaySnapshot] values cross out).
     */
    suspend fun daySnapshots(): List<DaySnapshot> {
        checkConsented()
        val suppressed = medicationDao.latestTreatment()?.modeRaw == "suppressed"
        return SnapshotProjector.project(
            symptoms = symptomDao.allSymptoms(),
            pain = symptomDao.allPain(),
            bleeding = symptomDao.allBleeding(),
            factors = symptomDao.allFactors(),
            entries = dayEntryDao.all(),
            imported = symptomDao.allImported(),
            suppressed = suppressed,
        )
    }

    // --- Insight cache / dismiss (gated; the InsightsCoordinator's persistence seam, Phase 22) ---

    /**
     * Load the durable gated-insight cache (D-04) — the instant paint the dashboard shows before any
     * recompute. Gates FIRST, then decodes each [CachedInsight.payload] JSON blob back to an [Insight].
     *
     * RESILIENT DECODE (T-08-04 posture): a corrupt / null payload row is SKIPPED via
     * `runCatching { ... }.getOrNull()` — never fatal — so one bad cache row can never crash the
     * dashboard. Returns `[]` when the cache is empty. Never fabricates.
     */
    suspend fun loadCachedInsights(): List<Insight> {
        checkConsented()
        return insightsDao.allCached().mapNotNull { row ->
            row.payload?.let { json -> runCatching { insightJson.decodeFromString<Insight>(json) }.getOrNull() }
        }
    }

    /**
     * REPLACE-ALL the insight cache (D-02/D-14): gate, [InsightsDao.clearCached], then insert one
     * [CachedInsight] per fresh [Insight]. The cache is invalidated wholesale on recompute (keyed by
     * `fingerprint`, never day-keyed), so this mirrors the DAO's clear-then-insert contract.
     *
     * PRIVACY BOUNDARY (T-22-01): `payload` encodes ONLY the gated [Insight] — NEVER a [DaySnapshot],
     * NEVER note prose. The flat category columns carry the same non-identifying labels DismissedInsight
     * stores.
     */
    suspend fun replaceCachedInsights(insights: List<Insight>, computedAtMillis: Long, fingerprint: String) {
        checkConsented()
        insightsDao.clearCached()
        insightsDao.insertCached(
            insights.map { insight ->
                CachedInsight(
                    id = insight.id.toString(),
                    signalKey = insight.signalKey,
                    kindRaw = insight.kind.raw,
                    windowRaw = insight.window.raw,
                    payload = insightJson.encodeToString(insight),
                    computedAtMillis = computedAtMillis,
                    fingerprint = fingerprint,
                )
            },
        )
    }

    /**
     * The dismissed `signalKey`s to exclude from the next recompute (D-09/D-11). Gates, then reads the
     * append-only [DismissedInsight] keys and dedupes on read (a `Set`) — a dismiss is non-destructive
     * (it never deletes logged rows), and duplicates collapse here.
     */
    suspend fun dismissedSignalKeys(): Set<String> {
        checkConsented()
        return insightsDao.dismissedSignalKeys().toSet()
    }

    // --- Delete-day (gated; delegates to the single-transaction cascade) ---

    suspend fun deleteDay(epochDay: Long) {
        checkConsented()
        deleteDayDao.deleteDay(epochDay)
    }

    /**
     * The single serialization boundary for the insight cache (the `:app` export module already depends
     * on kotlinx-serialization). `ignoreUnknownKeys` keeps a forward-compatible decode: a payload written
     * by a newer Insight shape still decodes its known fields rather than throwing.
     */
    private val insightJson: Json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Upper bound on a saved medication's free-text name/note, enforced at this write seam. */
        private const val MAX_MED_TEXT = 200

        /** Production wiring: pull the DAOs off the database. */
        fun from(consent: ConsentStore, db: EndoDatabase): EndoRepository = EndoRepository(
            consent = consent,
            dayEntryDao = db.dayEntryDao(),
            symptomDao = db.symptomDao(),
            medicationDao = db.medicationDao(),
            insightsDao = db.insightsDao(),
            deleteDayDao = db.deleteDayDao(),
        )
    }
}
