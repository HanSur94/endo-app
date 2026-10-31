package com.endoapp.app.data

/**
 * The app-level GDPR consent state (ONBD-01) — the value the whole app routes on before ANY
 * health-data processing.
 *
 * ## Three states (Android), not two (iOS)
 * iOS `ConsentState` is 2-state (`notConsented` / `consented(Date)`) because `UserDefaults` is
 * SYNCHRONOUS — the flag is readable the instant the app launches. Android's DataStore is
 * ASYNCHRONOUS, so there is a window at startup before the first emission where the flag is not yet
 * known. [Unknown] models exactly that window.
 *
 * ## Fail closed
 * [Unknown] counts as NOT consented everywhere (the repository gate treats anything that is not
 * [Consented] as a refusal). The three-way split is why the Phase-19 `AppGate` needs a loading arm
 * rather than briefly flashing the gated UI.
 *
 * [Consented] carries `grantedAtMillis` so the privacy / withdrawal copy can say "you consented on
 * <date>" — the same grant timestamp iOS stores.
 */
sealed interface ConsentState {

    /** DataStore has not yet emitted — the flag is unknown. Treated as NOT consented (fail closed). */
    data object Unknown : ConsentState

    /** No consent on record (no stored flag). Health processing is refused. */
    data object NotConsented : ConsentState

    /** Explicit consent recorded at [grantedAtMillis] (epoch millis). */
    data class Consented(val grantedAtMillis: Long) : ConsentState
}
