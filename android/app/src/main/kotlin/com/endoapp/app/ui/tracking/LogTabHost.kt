// EndoApp (:app) — the intra-Log host (ATRK-01). Owns the in-Log `rememberSaveable` stack, renders
// the current route, pops on system back when nested, and observes the (non-sensitive)
// [CheckInRouter] flag — but ONLY here, inside the consented Log subtree (threat T-20-11), so a
// reminder deep-link lands on the consent gate first and can never construct health UI pre-consent.
//
// NO `NavHost`, NO navigation dependency. The CheckIn / DayLog / Settings arms render temporary inline
// placeholders this plan; each later plan (02/03/04) replaces its arm with the real screen. This host
// stays free of any notification API — the [onDayLogged] seam is where Plan 05 wires the reminder
// auto-suppress consumer.
package com.endoapp.app.ui.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.endoapp.app.CheckInRouter
import com.endoapp.app.content.ShellCopy
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.designsystem.LocalBloomColors
import kotlinx.coroutines.flow.Flow

/**
 * The Log-tab host. [recentDays] is the live day history off the consent-gated repository;
 * [router] is observed here (and nowhere pre-consent) to push [TrackingRoute.CheckIn] on a
 * reminder deep-link; [onWithdraw] backs the temporary withdraw affordance (graduates into the real
 * Settings screen in Plan 04); [onDayLogged] is a reserved no-op seam Plan 05 wires to the reminder
 * auto-suppress consumer (kept here so this host imports no notification API).
 */
@Composable
fun LogTabHost(
    recentDays: Flow<List<DayEntry>>,
    router: CheckInRouter,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") onDayLogged: () -> Unit = {},
) {
    val stack = rememberTrackingBackStack()
    val days by recentDays.collectAsState(initial = emptyList())

    // Observe the deep-link flag ONLY inside this consented subtree: on set, push CheckIn + reset.
    LaunchedEffect(router.pendingCheckIn) {
        if (router.pendingCheckIn) {
            stack.push(TrackingRoute.CheckIn)
            router.pendingCheckIn = false
        }
    }

    // System back pops the in-Log stack only when nested; at LogHome it defers to the Phase-19
    // predictive-back-to-Log / exit rule (this handler does not register).
    BackHandler(enabled = stack.canPop) { stack.pop() }

    Box(modifier = modifier.fillMaxSize()) {
        when (val route = stack.current) {
            TrackingRoute.LogHome -> LogHomeScreen(
                recentDays = days,
                onCheckIn = { stack.push(TrackingRoute.CheckIn) },
                onEditDay = { epochDay -> stack.push(TrackingRoute.DayLog(epochDay)) },
                onOpenSettings = { stack.push(TrackingRoute.Settings) },
            )
            TrackingRoute.CheckIn -> RoutePlaceholder("log.route.checkIn")
            is TrackingRoute.DayLog -> RoutePlaceholder("log.route.dayLog")
            TrackingRoute.Settings -> SettingsPlaceholder(onWithdraw)
        }
    }
}

/** A temporary inline placeholder for a route a later plan fills. No user-facing copy (just a testTag). */
@Composable
private fun RoutePlaceholder(testTag: String) {
    Box(modifier = Modifier.fillMaxSize().testTag(testTag), contentAlignment = Alignment.Center) {}
}

/**
 * The temporary Settings-route placeholder. Hosts the withdraw affordance previewing the Plan-04
 * graduation; its label reuses the registered [ShellCopy.WITHDRAW] (no new copy escapes the gate).
 */
@Composable
private fun SettingsPlaceholder(onWithdraw: () -> Unit) {
    val colors = LocalBloomColors.current
    Box(modifier = Modifier.fillMaxSize().testTag("log.route.settings"), contentAlignment = Alignment.Center) {
        TextButton(onClick = onWithdraw, modifier = Modifier.testTag("log.settings.withdraw")) {
            Text(ShellCopy.WITHDRAW, color = colors.inkMuted)
        }
    }
}
