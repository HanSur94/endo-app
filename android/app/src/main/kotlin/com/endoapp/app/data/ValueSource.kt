// EndoApp (:app) — per-field provenance value types (port of iOS `ValueSource` / `DaySnapshotSource`
// from Sources/EndoApp/Health/ImportedSampleValue.swift, D-03).
//
// These live in the DATA layer, NOT the report package — the repository (which produces them) must
// not depend on the report. The report/UI consumes them to render the per-section SOURCE label
// ("Self-reported" vs "from Health Connect"). The label WORDS arrive later (ReportCopy); this file
// carries only the machine-readable provenance flag.
//
// HONESTY (SC3, extended to provenance): every [DaySnapshotSource] field is NULLABLE, so a
// denied/empty day fabricates NO badge — absence ≠ badge.
package com.endoapp.app.data

/**
 * Per-field provenance flag (D-03). A merged day field carries this so the UI knows whether the
 * winning value was logged manually or imported from Health Connect.
 *
 * The iOS `.appleHealth` case is renamed [HEALTH_CONNECT] here (State-of-the-Art: Android imports
 * from Health Connect, not Apple Health). `null` — "neither source present" — is modeled at the read
 * site (a nullable field), not as a case here: the source of a *present* value is one of these two.
 */
enum class ValueSource {
    MANUAL,
    HEALTH_CONNECT,
}

/**
 * Per-field provenance for one merged day (D-03). A SEPARATE, store-derived read the badge/report UI
 * consumes — kept OUT of the `:insights` `DaySnapshot` so the engine contract stays untouched.
 *
 * Each field is [ValueSource.MANUAL] when the day's winning value came from manual logging,
 * [ValueSource.HEALTH_CONNECT] when it came from an imported sample, or `null` when NEITHER source
 * contributed (so a denied/empty day fabricates no badge).
 */
data class DaySnapshotSource(
    val flow: ValueSource? = null,
    val sleep: ValueSource? = null,
    val activity: ValueSource? = null,
    val food: ValueSource? = null,
)
