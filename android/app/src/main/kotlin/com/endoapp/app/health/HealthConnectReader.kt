// The REAL Health Connect read adapter (Phase 21, AHC-03) — the thin `HealthConnectClient` shell
// behind the [HealthReader] seam (the iOS `SystemHealthDataReading` analog). This is the SOLE place
// `androidx.health.*` record types are pulled apart; it maps each record to an [ImportedSample] value
// row via [HealthRecordMapping] INSIDE the boundary, so nothing HC-typed crosses [ReadResult].
//
// SYNC MODEL (RESEARCH Pattern 4): a changes-token DIFFERENTIAL cursor, not a per-day `readRecords`
// loop (that burns quota and misses deletions). A null token seeds one bounded ranged read + mints a
// fresh cursor; a non-null token pages `getChanges` (Upsertion → mapped upsert, Deletion → propagated
// id), skipping our OWN-package writes (T-21-02c — we write none, so a self-write can never loop).
//
// TOKEN EXPIRY: connect-client 1.1.0 signals a stale cursor via `ChangesResponse.changesTokenExpired`
// (there is NO `ChangesTokenExpiredException` in this version — verified against the AAR). Expiry is
// surfaced as `ReadResult(tokenExpired = true, nextToken = null)`; the import coordinator (Plan 03)
// owns the reseed policy.
//
// DEVICE-GATED: real behavior needs a real HC provider; here it compiles + typechecks, and all
// import / merge / idempotency / deletion logic is proven via the FakeHealthReader in Plan 03.
package com.endoapp.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.insightsbridge.HealthDomain
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * The real [HealthReader] over a [HealthConnectClient].
 *
 * @param client the Health Connect client (constructed only when availability is AVAILABLE — Plan 01/04).
 * @param ownPackageName this app's package name; an `UpsertionChange` whose `dataOrigin.packageName`
 *   equals it is SKIPPED (own-package guard — we write no health data, so a re-imported self-write can
 *   never loop). Injected (not read from a `Context` here) to keep the adapter free of Android context.
 * @param zone supplies the DEVICE's current zone for bucketing (defaults to `ZoneId.systemDefault()`);
 *   read at map time so a mid-session travel is reflected.
 * @param clock supplies `importedAtMillis` (defaults to wall-clock); injected for determinism in tests.
 */
class HealthConnectReader(
    private val client: HealthConnectClient,
    private val ownPackageName: String,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
) : HealthReader {

    override suspend fun grantedPermissions(): Set<String> =
        // Read PER SESSION — never cached (a grant can be revoked or unused-app auto-reset; Pitfall 3).
        client.permissionController.getGrantedPermissions()

    override suspend fun read(domain: HealthDomain, token: String?): ReadResult {
        if (token == null) return seed(domain)
        return differential(domain, token)
    }

    /** Seed: one bounded ranged read per record type + a fresh changes cursor to persist. */
    private suspend fun seed(domain: HealthDomain): ReadResult {
        val recordTypes = recordTypesFor(domain)
        val upserts = readSeedRecords(domain).mapNotNull { mapRecord(it) }
        val nextToken = client.getChangesToken(ChangesTokenRequest(recordTypes = recordTypes))
        return ReadResult(upserts = upserts, deletedHcRecordIds = emptyList(), nextToken = nextToken)
    }

    /** Differential: page `getChanges` from [token], partitioning upserts vs deletions. */
    private suspend fun differential(domain: HealthDomain, token: String): ReadResult {
        val upserts = mutableListOf<ImportedSample>()
        val deletions = mutableListOf<String>()
        var next = token
        while (true) {
            val response = client.getChanges(next)
            if (response.changesTokenExpired) {
                // Stale cursor → signal re-seed; the coordinator (Plan 03) clears + re-seeds next sync.
                return ReadResult(emptyList(), emptyList(), nextToken = null, tokenExpired = true)
            }
            for (change in response.changes) {
                when (change) {
                    is UpsertionChange -> {
                        val record = change.record
                        // Own-package guard (T-21-02c): ignore our own writes (we write none anyway).
                        if (record.metadata.dataOrigin.packageName == ownPackageName) continue
                        mapRecord(record)?.let(upserts::add)
                    }
                    is DeletionChange -> deletions.add(change.recordId)
                }
            }
            next = response.nextChangesToken
            if (!response.hasMore) break
        }
        return ReadResult(upserts = upserts, deletedHcRecordIds = deletions, nextToken = next)
    }

    /** The bounded seed ranged read (history-gated by the granted `READ_HEALTH_DATA_HISTORY`). */
    private suspend fun readSeedRecords(domain: HealthDomain): List<Record> {
        val filter = TimeRangeFilter.after(Instant.EPOCH)
        return when (domain) {
            HealthDomain.CYCLE -> readAll<MenstruationFlowRecord>(filter)
            HealthDomain.SLEEP -> readAll<SleepSessionRecord>(filter)
            HealthDomain.WORKOUTS -> readAll<ExerciseSessionRecord>(filter)
            HealthDomain.NUTRITION -> readAll<NutritionRecord>(filter)
        }
    }

    /** Page a `readRecords` call fully (private + inline so the reified type can access `client`). */
    private suspend inline fun <reified T : Record> readAll(filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val request = ReadRecordsRequest(
                recordType = T::class,
                timeRangeFilter = filter,
                pageToken = pageToken,
            )
            val response = client.readRecords(request)
            // Own-package writes are filtered at mapRecord time; keep the raw records here.
            out += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }

    /**
     * Map a Health Connect record to an [ImportedSample] value row via [HealthRecordMapping]. No HC
     * record type escapes: an unrecognised record class contributes nothing (null), never a crash.
     */
    private fun mapRecord(record: Record): ImportedSample? {
        val z = zone()
        return when (record) {
            is MenstruationFlowRecord ->
                HealthRecordMapping.menstruationSample(record.metadata.id, record.time, record.flow, z, clock)
            is SleepSessionRecord ->
                HealthRecordMapping.sleepSample(
                    hcRecordId = record.metadata.id,
                    startTime = record.startTime,
                    endTime = record.endTime,
                    stages = record.stages.map {
                        HealthRecordMapping.SleepStageValue(it.stage, it.startTime, it.endTime)
                    },
                    zone = z,
                    clock = clock,
                )
            is ExerciseSessionRecord ->
                HealthRecordMapping.exerciseSample(
                    record.metadata.id, record.startTime, record.endTime, record.exerciseType, z, clock,
                )
            is NutritionRecord ->
                HealthRecordMapping.nutritionSample(record.metadata.id, record.startTime, z, clock)
            else -> null
        }
    }

    private fun recordTypesFor(domain: HealthDomain): Set<KClass<out Record>> = when (domain) {
        HealthDomain.CYCLE -> setOf(MenstruationFlowRecord::class)
        HealthDomain.SLEEP -> setOf(SleepSessionRecord::class)
        HealthDomain.WORKOUTS -> setOf(ExerciseSessionRecord::class)
        HealthDomain.NUTRITION -> setOf(NutritionRecord::class)
    }
}
