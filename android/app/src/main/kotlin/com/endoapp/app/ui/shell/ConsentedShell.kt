package com.endoapp.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.endoapp.app.CheckInRouter
import com.endoapp.app.content.PlaceholderCopy
import com.endoapp.app.content.ShellCopy
import com.endoapp.app.designsystem.BloomNavigationBar
import com.endoapp.app.designsystem.BloomTabIcons
import com.endoapp.app.designsystem.LocalBloomColors

/**
 * The consented 3-tab shell — composed ONLY inside [com.endoapp.app.AppGate]'s `Consented` arm, so
 * the tab surface is unconstructible pre-consent by construction (ASHL-01 / threat T-19-01).
 *
 * A Material 3 [Scaffold] with the [BloomNavigationBar] as its `bottomBar`, over a [ConsentedTab]
 * `rememberSaveable` selection (NOT a `NavHost` — no navigation library). Each tab renders a Bloom
 * [TabPlaceholder] card marking the phase that fills it (Log → 20, Insights/Explore → 22).
 *
 * **Edge-to-edge (mandatory at targetSdk 36):** `Scaffold` consumes the system-bar `WindowInsets`
 * and hands the body `innerPadding`; the `bottomBar` `NavigationBar` handles the `navigationBars`
 * inset itself. The placeholder body pads from `innerPadding`, so content never draws under the
 * status/nav bars.
 *
 * **Predictive back:** [androidx.activity.compose.PredictiveBackHandler] is enabled ONLY when a
 * non-Log tab is selected — a back gesture animates the return to the start tab (Log). On Log the
 * handler is disabled, so back falls through to the system default (exit the app). Back is never
 * intercepted generally (predictive back stays default-on).
 *
 * The [router] deep-link flag is observed ONLY here (inside the consented subtree) — never in the
 * gate's pre-consent arms — so a notification/deep link lands on the gate while unconsented.
 */
@Composable
fun ConsentedShell(
    router: CheckInRouter,
    onWithdraw: () -> Unit,
) {
    val colors = LocalBloomColors.current
    var selectedTab by rememberSaveable { mutableStateOf(ConsentedTab.Log) }

    // Consume the non-sensitive deep-link flag. Real target (jump to the Log check-in) is Phase 20;
    // here we simply select Log and reset the flag so the entry point lands on the shell.
    LaunchedEffect(router.pendingCheckIn) {
        if (router.pendingCheckIn) {
            selectedTab = ConsentedTab.Log
            router.pendingCheckIn = false
        }
    }

    Scaffold(
        bottomBar = {
            BloomNavigationBar(
                tabs = ConsentedTab.entries,
                selected = selectedTab,
                onSelect = { selectedTab = it },
                testTag = { it.testTag },
                label = { it.label },
                icon = { iconFor(it) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                ConsentedTab.Log -> TabPlaceholder(
                    eyebrow = PlaceholderCopy.LOG_EYEBROW,
                    heading = PlaceholderCopy.LOG_HEADING,
                    body = PlaceholderCopy.LOG_BODY,
                    testTag = "tab.log.body",
                )
                ConsentedTab.Insights -> TabPlaceholder(
                    eyebrow = PlaceholderCopy.INSIGHTS_EYEBROW,
                    heading = PlaceholderCopy.INSIGHTS_HEADING,
                    body = PlaceholderCopy.INSIGHTS_BODY,
                    testTag = "tab.insights.body",
                )
                ConsentedTab.Explore -> TabPlaceholder(
                    eyebrow = PlaceholderCopy.EXPLORE_EYEBROW,
                    heading = PlaceholderCopy.EXPLORE_HEADING,
                    body = PlaceholderCopy.EXPLORE_BODY,
                    testTag = "tab.explore.body",
                )
            }

            // Temporary withdraw affordance so the withdraw-re-gate invariant is exercisable this
            // phase; the real Settings entry (with a confirmation dialog) is Phase 20. Its label is
            // registered in ShellCopy so it does not escape the CopyAudit gate.
            TextButton(
                onClick = onWithdraw,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .testTag("shell.withdraw"),
            ) {
                Text(ShellCopy.WITHDRAW, color = colors.inkMuted)
            }
        }
    }
}

/** The bundled Bloom glyph for each tab (design-system icons resolved in the shell layer). */
private fun iconFor(tab: ConsentedTab): ImageVector = when (tab) {
    ConsentedTab.Log -> BloomTabIcons.logGlyph
    ConsentedTab.Insights -> BloomTabIcons.insightsGlyph
    ConsentedTab.Explore -> BloomTabIcons.exploreGlyph
}
