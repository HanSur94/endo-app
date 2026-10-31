package com.endoapp.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A NON-sensitive deep-link / notification entry flag — the seam a reminder notification or deep
 * link flips when the app is entered from outside (threat T-19-01b, Spoofing).
 *
 * It carries NO health data — only a boolean "the user tapped a check-in entry point". It is
 * observed ONLY inside the `Consented` branch of [AppGate] (never in the `Unknown`/`NotConsented`
 * arms), so a deep link fired while unconsented lands on the gate and can never bypass it. The real
 * navigation target (jump to the Log tab's check-in) is wired in Phase 20; here the shell simply
 * consumes and resets the flag.
 */
class CheckInRouter {

    /** True when an external entry point (notification/deep link) requested the check-in surface. */
    var pendingCheckIn by mutableStateOf(false)
}
