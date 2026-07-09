// EndoApp (:app) — the intra-Log navigation model (ATRK-01). A lightweight `rememberSaveable`
// screen stack INSIDE the consented Log subtree — deliberately NOT a `NavHost` and adds NO
// navigation dependency (keeps the Phase-19 no-NavHost posture). The whole stack lives only where
// [com.endoapp.app.ui.shell.ConsentedShell] composes it, so health destinations stay unconstructible
// pre-consent by construction.
package com.endoapp.app.ui.tracking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * A destination within the Log tab. The stack starts at [LogHome]; the CTA / rows / settings gear
 * push [CheckIn] / [DayLog] / [Settings]. A typed sealed interface — never an `Int` index.
 */
sealed interface TrackingRoute {
    /** The Log-home host screen (the stack root). */
    data object LogHome : TrackingRoute

    /** The Daily Check-In flow (Plan 02 fills this arm). */
    data object CheckIn : TrackingRoute

    /** The Day Log editor for a specific day (Plan 03 fills this arm). */
    data class DayLog(val epochDay: Long) : TrackingRoute

    /** The Settings screen (Plan 04 fills this arm). */
    data object Settings : TrackingRoute

    /** The Health Connect connect/import screen, reached from the Settings connect entry (Phase 21). */
    data object HealthImport : TrackingRoute

    /** The encrypted export/import screen, reached from the Settings "Your data" entry (Phase 23). */
    data object ExportImport : TrackingRoute
}

/**
 * The in-Log back stack — a `mutableStateListOf`-backed holder with push/pop, config-change-durable
 * via [Saver]. [current] is the top route; [canPop] is true when there is somewhere to pop back to.
 */
@Stable
class TrackingBackStack(initial: List<TrackingRoute>) {
    private val entries = mutableStateListOf<TrackingRoute>().apply { addAll(initial) }

    /** The currently-shown route (the top of the stack). */
    val current: TrackingRoute get() = entries.last()

    /** True when the stack has more than the root — i.e. a back would pop rather than exit the tab. */
    val canPop: Boolean get() = entries.size > 1

    /** Push [route] onto the stack. */
    fun push(route: TrackingRoute) { entries.add(route) }

    /** Pop the top route if there is one below it (never pops the root). */
    fun pop() { if (entries.size > 1) entries.removeAt(entries.lastIndex) }

    private fun snapshot(): List<TrackingRoute> = entries.toList()

    companion object {
        /** Serialises each route to a short token so the whole stack survives config change. */
        val Saver = listSaver<TrackingBackStack, String>(
            save = { it.snapshot().map(::encode) },
            restore = { TrackingBackStack(it.map(::decode)) },
        )

        private fun encode(route: TrackingRoute): String = when (route) {
            TrackingRoute.LogHome -> "H"
            TrackingRoute.CheckIn -> "C"
            is TrackingRoute.DayLog -> "D:${route.epochDay}"
            TrackingRoute.Settings -> "S"
            TrackingRoute.HealthImport -> "HC"
            TrackingRoute.ExportImport -> "EX"
        }

        private fun decode(token: String): TrackingRoute = when {
            token == "H" -> TrackingRoute.LogHome
            token == "C" -> TrackingRoute.CheckIn
            token == "S" -> TrackingRoute.Settings
            token == "HC" -> TrackingRoute.HealthImport
            token == "EX" -> TrackingRoute.ExportImport
            token.startsWith("D:") -> TrackingRoute.DayLog(token.removePrefix("D:").toLong())
            else -> TrackingRoute.LogHome
        }
    }
}

/** Remember a [TrackingBackStack] rooted at [TrackingRoute.LogHome], durable across config change. */
@Composable
fun rememberTrackingBackStack(): TrackingBackStack =
    rememberSaveable(saver = TrackingBackStack.Saver) {
        TrackingBackStack(listOf(TrackingRoute.LogHome))
    }
