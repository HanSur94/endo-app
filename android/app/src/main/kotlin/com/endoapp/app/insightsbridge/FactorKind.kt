// EndoApp (:app) — the discriminator for the unified FactorReading model (LIFE-01/02/03, D-01/D-02).
//
// Kotlin port of `Sources/EndoApp/Domain/FactorKind.swift`. This enum lives in :app, NOT in
// :insights — the Phase-17 golden-JSON header cross-checks these 7 raws. The raw values equal the
// Swift case names VERBATIM (`enum FactorKind: String` with no explicit `= "..."`, so raw == case
// name): food / activity / sleep / medication / stress / mood / other.
//
// Backing convention: persisted as the optional `kindRaw: String?` on `FactorReading` — decoded here
// at the SnapshotProjector bridge, never fabricated.
//
// Robustness (T-18-06): [fromRaw] decodes defensively — an unknown or `null` raw maps to [OTHER],
// never throws. This mirrors the iOS `init(raw:)`.
package com.endoapp.app.insightsbridge

/**
 * The kind of lifestyle/context factor a `FactorReading` carries (D-01). String-backed so a corrupt
 * or future raw never crashes the projection.
 */
enum class FactorKind(val raw: String) {
    FOOD("food"),
    ACTIVITY("activity"),
    SLEEP("sleep"),
    MEDICATION("medication"),
    STRESS("stress"),
    MOOD("mood"),
    OTHER("other");

    companion object {
        /** Defensive decode (T-18-06): an unknown or `null` raw maps to [OTHER] rather than crashing. */
        fun fromRaw(raw: String?): FactorKind =
            entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}
