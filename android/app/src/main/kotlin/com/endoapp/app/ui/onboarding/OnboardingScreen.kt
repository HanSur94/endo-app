package com.endoapp.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ShellCopy
import com.endoapp.app.designsystem.BloomPrimaryButton
import com.endoapp.app.designsystem.LocalBloomColors

/**
 * The pre-consent onboarding/consent screen — composed ONLY inside [com.endoapp.app.AppGate]'s
 * `NotConsented` arm. Minimal-but-real this phase (Plan 04 enriches it with the hero, disclaimer
 * banner, privacy link, and Bloom styling): the two load-bearing affordances with their stable
 * testTags — the "I consent" primary CTA (the only grant site) and the low-emphasis "Not now"
 * decline (an explicit, fail-closed no-op, required so declining is a visible choice).
 */
@Composable
fun OnboardingScreen(
    onConsent: () -> Unit,
    onDecline: () -> Unit,
) {
    val colors = LocalBloomColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BloomPrimaryButton(
            text = ShellCopy.CONSENT_GRANT,
            onClick = onConsent,
            modifier = Modifier.testTag("consent.grantButton"),
        )
        TextButton(
            onClick = onDecline,
            modifier = Modifier.testTag("consent.declineButton"),
        ) {
            Text(ShellCopy.CONSENT_DECLINE, color = colors.inkMuted)
        }
    }
}
