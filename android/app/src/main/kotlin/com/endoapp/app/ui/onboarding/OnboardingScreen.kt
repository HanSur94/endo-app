package com.endoapp.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.Disclaimer
import com.endoapp.app.content.OnboardingCopy
import com.endoapp.app.content.ShellCopy
import com.endoapp.app.designsystem.BloomGlyphs
import com.endoapp.app.designsystem.BloomPrimaryButton
import com.endoapp.app.designsystem.DisclaimerBanner
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomH2
import com.endoapp.app.designsystem.bloomTitle

/**
 * The pre-consent Bloom onboarding + consent screen (ASHL-01/02) — composed ONLY inside
 * [com.endoapp.app.AppGate]'s `NotConsented` arm. It surfaces, in this order (the GDPR informed-consent
 * focus order): a rose hero heart + welcome title/body, the petal [DisclaimerBanner] (rendering the
 * single-source [Disclaimer.text]), a plum privacy link that opens the offline [PrivacyPolicyScreen]
 * BEFORE the consent action, and the consent section — a `bloomH2` heading + explainer + the
 * [BloomPrimaryButton] "I consent" (the only grant site) + a low-emphasis "Not now" decline.
 *
 * The consent affordances keep the exact Plan-02 contract — the `consent.grantButton` /
 * `consent.declineButton` testTags and the `onConsent` / `onDecline` callback signature — so the
 * `AppGate` grant/decline wiring is unchanged. Consent is an explicit affirmative action; declining is
 * an explicit, fail-closed no-op (stores nothing, stays on the gate).
 */
@Composable
fun OnboardingScreen(
    onConsent: () -> Unit,
    onDecline: () -> Unit,
) {
    val colors = LocalBloomColors.current
    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            // safeDrawingPadding covers systemBars + displayCutout + ime, so the "I consent" button is
            // never under the status/nav bar (nor a keyboard, if one ever appears here).
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // (1) Hero — rose heart + welcome title + welcome body.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = BloomGlyphs.heartGlyph,
                contentDescription = null,
                tint = colors.rose,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = OnboardingCopy.HERO_TITLE,
                style = bloomTitle,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = OnboardingCopy.WELCOME_BODY,
                style = bloomBody,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
        }

        // (2) Disclaimer — "not medical advice / not a medical device", reachable before consent.
        DisclaimerBanner(
            text = Disclaimer.text,
            modifier = Modifier.testTag("onboarding.disclaimer"),
        )

        // (3) Privacy link — opens the offline policy BEFORE the consent action.
        TextButton(
            onClick = { showPrivacy = true },
            modifier = Modifier
                .sizeIn(minHeight = 48.dp)
                .testTag("onboarding.privacyLink"),
        ) {
            Icon(
                imageVector = BloomGlyphs.lockGlyph,
                contentDescription = null,
                tint = colors.plum,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = OnboardingCopy.PRIVACY_LINK,
                style = bloomBody,
                color = colors.plum,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // (4) Consent section — heading + explainer + explicit affirmative accept + explicit decline.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = OnboardingCopy.CONSENT_HEADING,
                style = bloomH2,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = OnboardingCopy.CONSENT_EXPLAINER,
                style = bloomBody,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
            BloomPrimaryButton(
                text = ShellCopy.CONSENT_GRANT,
                onClick = onConsent,
                modifier = Modifier.testTag("consent.grantButton"),
            )
            TextButton(
                onClick = onDecline,
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .testTag("consent.declineButton"),
            ) {
                Text(ShellCopy.CONSENT_DECLINE, style = bloomBody, color = colors.inkMuted)
            }
        }
    }

    // The offline privacy policy, presented over onboarding; dismisses on back.
    if (showPrivacy) {
        PrivacyPolicyScreen(onDismiss = { showPrivacy = false })
    }
}
