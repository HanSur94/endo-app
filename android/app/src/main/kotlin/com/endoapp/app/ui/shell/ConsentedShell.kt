package com.endoapp.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.platform.testTag
import com.endoapp.app.CheckInRouter
import com.endoapp.app.content.ShellCopy
import com.endoapp.app.designsystem.LocalBloomColors

/**
 * The consented 3-tab shell — composed ONLY inside [com.endoapp.app.AppGate]'s `Consented` arm.
 * Minimal-but-real this phase (Plan 03 enriches the Bloom nav chrome, insets, predictive back, and
 * fills the placeholders): a Material 3 `Scaffold` with a bottom `NavigationBar` over a
 * [ConsentedTab] `rememberSaveable` selection (NOT a `NavHost`). Each tab body is an empty
 * placeholder Box this phase; Phases 20/22 fill them.
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
            NavigationBar(containerColor = colors.surface) {
                ConsentedTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { /* Bloom tab glyphs land in Plan 03 */ },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag(tab.testTag),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.plum,
                            selectedTextColor = colors.plum,
                            unselectedIconColor = colors.inkMuted,
                            unselectedTextColor = colors.inkMuted,
                            indicatorColor = colors.petal,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            // Empty per-tab placeholder body this phase (Plan 03 fills the Bloom scaffolds).
            when (selectedTab) {
                ConsentedTab.Log -> Box(Modifier.testTag("tab.log.body"))
                ConsentedTab.Insights -> Box(Modifier.testTag("tab.insights.body"))
                ConsentedTab.Explore -> Box(Modifier.testTag("tab.explore.body"))
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
