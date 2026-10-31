// The injectable Health Connect read seam (Phase 21, AHC-03). The Android analog of the iOS
// `HealthDataReading` protocol: the SOLE boundary where Health Connect records become app value rows.
//
// SEAM INVARIANT: NO `androidx.health.*` type appears in this interface's signature. The real
// adapter ([HealthConnectReader]) maps HC records to [ImportedSample] value rows INSIDE the boundary,
// so import / merge / idempotency / deletion propagation are all JVM-testable off a fake reader
// (Plan 03's FakeHealthReader) WITHOUT an emulator or a real HC provider. Everything downstream —
// `ImportedSignals`, `SnapshotProjector` (manual-wins / nil-honest) — already exists and is
// test-pinned; this seam simply feeds it the four mapped field sets.
package com.endoapp.app.health

import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.insightsbridge.HealthDomain

/**
 * Reads new/changed imported samples for a [HealthDomain] since a per-domain changes token.
 *
 * The single method that touches Health Connect returns ALREADY-MAPPED [ImportedSample] value rows
 * (plus deletions and the advanced cursor) — no `androidx.health.*` record type ever crosses out.
 * `grantedPermissions()` is read PER SESSION (never cached — a grant can be revoked or auto-reset).
 */
interface HealthReader {

    /** The Health Connect permissions currently granted. Read per session; never cached (Pitfall 3). */
    suspend fun grantedPermissions(): Set<String>

    /**
     * Read the [domain]'s new/changed rows since [token] (`null` ⇒ a seed ranged read that also mints
     * a fresh cursor). An empty [ReadResult] models denied-OR-granted-but-empty identically (read-status
     * honesty, mirroring the iOS fixture). Never throws on a stale cursor — signals re-seed via
     * [ReadResult.tokenExpired] instead so the coordinator (Plan 03) owns the reseed policy.
     */
    suspend fun read(domain: HealthDomain, token: String?): ReadResult
}

/**
 * The value result of a [HealthReader.read] — mapped upserts, propagated deletions, and the advanced
 * cursor. No `androidx.health.*` type crosses here (the mapping already happened inside the adapter).
 *
 * @property upserts already-mapped rows to idempotently upsert (keyed by `hcRecordId`).
 * @property deletedHcRecordIds `hcRecordId`s the provider reported deleted (propagate the deletion).
 * @property nextToken the advanced changes cursor to persist for the next sync (`null` after expiry).
 * @property tokenExpired `true` iff the supplied token was stale — the coordinator must re-seed
 *   (connect-client 1.1.0 signals expiry via `ChangesResponse.changesTokenExpired`, not an exception).
 */
data class ReadResult(
    val upserts: List<ImportedSample>,
    val deletedHcRecordIds: List<String>,
    val nextToken: String?,
    val tokenExpired: Boolean = false,
)
