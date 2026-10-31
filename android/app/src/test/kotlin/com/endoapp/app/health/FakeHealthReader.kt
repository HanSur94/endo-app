package com.endoapp.app.health

import com.endoapp.app.insightsbridge.HealthDomain

/**
 * A scripted [HealthReader] test double — the JVM analog of the iOS `FixtureHealthDataReading`. It
 * lets the [HealthImportCoordinator] be exercised (import / idempotency / deletion / nil-honest /
 * token-expiry) with NO emulator and NO real Health Connect provider: the whole `androidx.health.*`
 * boundary is replaced by a per-domain queue of pre-baked [ReadResult]s.
 *
 * Each `read(domain, token)` pops the NEXT scripted result for that domain (a real provider would
 * differ each sync); a domain with no script — or a drained queue — yields [EMPTY], which models a
 * DENIED-or-granted-but-empty read IDENTICALLY (read-status honesty, mirroring the iOS `.empty`
 * fixture). A scripted `ReadResult(tokenExpired = true)` reproduces a stale-cursor read so the
 * coordinator's reseed path is testable.
 *
 * [calls] records every `(domain, token)` read in order so a test can assert the coordinator read the
 * persisted cursor (and did NOT read at all when it refused pre-consent).
 */
class FakeHealthReader(
    scripts: Map<HealthDomain, List<ReadResult>> = emptyMap(),
    private val granted: Set<String> = emptySet(),
) : HealthReader {

    private val queues: Map<HealthDomain, ArrayDeque<ReadResult>> =
        scripts.mapValues { (_, results) -> ArrayDeque(results) }

    /** Every `read` call in order — `(domain, token-supplied)`. */
    val calls: MutableList<Pair<HealthDomain, String?>> = mutableListOf()

    override suspend fun grantedPermissions(): Set<String> = granted

    override suspend fun read(domain: HealthDomain, token: String?): ReadResult {
        calls += domain to token
        val queue = queues[domain]
        return if (queue == null || queue.isEmpty()) EMPTY else queue.removeFirst()
    }

    companion object {
        /** A denied OR granted-but-empty read — contributes nothing, mints no cursor (nil-honest). */
        val EMPTY = ReadResult(upserts = emptyList(), deletedHcRecordIds = emptyList(), nextToken = null)
    }
}
