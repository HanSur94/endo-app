// SCAT-04 — the cross-tab scatter deep-link routing seam. The Android mirror of the iOS
// `ExploreScatterRouter.swift` (+ its `PendingScatter`), the analog of the in-app [CheckInRouter].
//
// A tiny router the `.correlation` card affordance (InsightCard) sets when the user taps "See the days
// behind this". `ExploreScreen` — built ONLY inside `AppGate`'s Consented arm (via [com.endoapp.app.ui.shell.ConsentedShell])
// — observes it and prefills the scatter X/Y axes + switches to Scatter mode, then resets it to null so a
// later identical tap re-triggers (consume-and-clear).
//
// SECURITY INVARIANT (T-16-11 / T-16-12, mirrors CheckInRouter's posture): the router carries ONLY a
// NON-SENSITIVE intent value — a [PendingScatter] wrapping two [ExploreSeries] (catalog series ids, which
// are already PUBLIC, non-sensitive identifiers). It carries NO health data and NEVER presents UI itself;
// presentation happens inside the Consented arm (`ExploreScreen`), so a deep-link can never surface health
// UI before consent. The payload is EPHEMERAL: there is NO persistence wrapper (no DataStore, no
// app-storage) — persisting a scatter-interest would leak health-interest metadata outside the
// consent-gated store. The payload is reset to null once consumed.
package com.endoapp.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.endoapp.insights.ExploreSeries

/**
 * The two scatter axes a `.correlation` deep-link carries: an (x, y) pair of quantitative [ExploreSeries].
 * A small `data class` (NOT a bare Pair) so the observing `LaunchedEffect(key)` restarts on a genuine axis
 * change. [ExploreSeries] is equal by id, so two [PendingScatter] compare equal iff both axes match.
 * Carries only catalog series ids (public, non-sensitive) — never health data.
 */
data class PendingScatter(
    /** The X axis the scatter prefills (a quantitative [ExploreSeries]). */
    val x: ExploreSeries,
    /** The Y axis the scatter prefills (a quantitative [ExploreSeries]). */
    val y: ExploreSeries,
)

/**
 * The cross-tab scatter deep-link router (SCAT-04). The `.correlation` card affordance sets
 * [pendingScatter]; the Consented-arm `ExploreScreen` reads it to prefill the scatter, then clears it.
 * Observed ONLY inside `AppGate`'s Consented arm (consent-by-construction), exactly like [CheckInRouter].
 */
class ExploreScatterRouter {

    /** When non-null, the Consented-arm `ExploreScreen` prefills the scatter with this (x, y) pair and
     *  switches to Scatter mode, then resets this to null. Ephemeral — never persisted (T-16-11). */
    var pendingScatter: PendingScatter? by mutableStateOf(null)
}
