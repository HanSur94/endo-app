// The consent-gated Health Connect import orchestrator (Phase 21, AHC-03 / AHC-04). The Android analog
// of the iOS `HealthImportService`: it turns [HealthReader] output into persisted [ImportedSample]
// rows through the Phase-18 consent-gated [EndoRepository], advancing a per-domain changes cursor so
// each sync is INCREMENTAL, and it survives cursor expiry by re-seeding — never crashing.
//
// SEPARATION INVARIANT (T-20-02d): this file imports NO notification / alarm / WorkManager API. Import
// is a FOREGROUND coroutine (Plan 05 triggers it from the UI); the reminders package stays the app's
// SOLE notification owner. The Phase-20 NotificationSeparationTest scans for and forbids any such
// import here.
//
// The merge is NOT rebuilt here: persisted rows feed the already-built manual-wins / nil-honest
// [com.endoapp.app.insightsbridge.SnapshotProjector] via `EndoRepository.daySnapshots()`.
package com.endoapp.app.health

import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.insightsbridge.HealthDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The result of an import run — the granted-permission surface the UI renders (AHC-04).
 *
 * @property grantedPermissions the Health Connect permissions currently granted (per-permission status
 *   for the settings/import UI — which of the four READ scopes the user allowed).
 * @property fullHistoryGranted `true` iff [HealthConnectPermissions.HISTORY] is granted → the UI shows
 *   the honest "full history" window label; `false` → the "last 30 days" label (HC caps a pre-grant
 *   read to ~30 days). This is the AHC-04 honest-window decision.
 */
data class ImportOutcome(
    val grantedPermissions: Set<String>,
    val fullHistoryGranted: Boolean,
)

/**
 * Reads each [HealthDomain] via the injected [HealthReader] (all seams — no concrete Android/HC type),
 * persists upserts idempotently (by `hcRecordId` — deterministic id + REPLACE), propagates deletions
 * (delete-by-`hcRecordId`), and advances the per-domain [ChangesTokenStore] cursor ONLY after a
 * successful persist. On cursor expiry it clears the domain (re-seed next sync). Runs off the main
 * thread ([Dispatchers.Default]).
 *
 * CONSENT (T-21-02d, belt + suspenders): [EndoRepository] already gates every persistence path
 * (`checkConsented()` throws pre-consent); this coordinator ADDITIONALLY refuses to run — and never
 * even reads — before consent.
 */
class HealthImportCoordinator(
    private val reader: HealthReader,
    private val repository: EndoRepository,
    private val changesTokenStore: ChangesTokenStore,
    private val consent: ConsentStore,
) {

    /**
     * Import every domain, then return the granted-permission surface for the UI window label.
     * @throws IllegalStateException if run before consent (fail closed, before any read).
     */
    suspend fun importAll(): ImportOutcome = withContext(Dispatchers.Default) {
        check(consent.state.value is ConsentState.Consented) {
            "HealthImportCoordinator ran before consent (ONBD-01) — import is consent-gated."
        }

        for (domain in HealthDomain.entries) {
            importDomain(domain)
        }

        val granted = reader.grantedPermissions()
        ImportOutcome(
            grantedPermissions = granted,
            fullHistoryGranted = granted.contains(HealthConnectPermissions.HISTORY),
        )
    }

    /**
     * Sync ONE domain: read since the persisted cursor (null ⇒ seed), persist upserts + deletions
     * through the gated repo, and advance the cursor. On expiry clear the cursor and return WITHOUT
     * advancing — the next run re-seeds. The token never advances ahead of the data it indexes.
     */
    private suspend fun importDomain(domain: HealthDomain) {
        val token = changesTokenStore.token(domain)
        val result = reader.read(domain, token)

        if (result.tokenExpired) {
            // Stale cursor → drop it and re-seed next sync. No partial advance, no crash (T-21-02e).
            changesTokenStore.clear(domain)
            return
        }

        // Idempotent upserts (REPLACE by the deterministic id / hcRecordId unique index).
        for (sample in result.upserts) {
            repository.upsertImported(sample)
        }
        // Propagate HC deletions.
        for (hcRecordId in result.deletedHcRecordIds) {
            repository.deleteImportedByHcRecordId(hcRecordId)
        }

        // Advance ONLY after the domain's data is committed (no data loss on a mid-sync failure).
        changesTokenStore.setToken(domain, result.nextToken)
    }
}
