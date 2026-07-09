package com.endoapp.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.PrivacyPolicyText
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomH2
import com.endoapp.app.designsystem.bloomH3

/**
 * The offline in-app privacy policy (ONBD-04) — a full-screen overlay presented over
 * [OnboardingScreen] when the user taps the privacy link, BEFORE the consent action (GDPR informed
 * consent). It renders the single-source [PrivacyPolicyText.body] (the Android-adapted, local-only
 * offline source of truth) on a Bloom `surface` background, scrollable, with `bloomH2`/`bloomH3`
 * headings + `body`. It dismisses on system/predictive back — the platform-native, label-free
 * affordance (a richer Settings entry is Phase 20).
 *
 * Rendered in the SAME composition (not a popup/dialog) so it composes deterministically over the
 * gate and its content is queryable on the Robolectric JVM lane.
 */
@Composable
fun PrivacyPolicyScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current

    // Back / predictive-back dismisses the policy and returns to onboarding.
    BackHandler(enabled = true) { onDismiss() }

    Column(
        modifier = modifier
            .testTag("privacyPolicy.body")
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The body is paragraph blocks separated by blank lines; each block after the lede is a
        // "Heading\nParagraph" pair. Render the lede as an H2, subsequent headings as H3.
        val blocks = PrivacyPolicyText.body.split("\n\n")
        blocks.forEachIndexed { index, block ->
            val lines = block.split("\n", limit = 2)
            when {
                index == 0 -> Text(text = block, style = bloomH2, color = colors.ink)
                lines.size == 2 -> {
                    Text(text = lines[0], style = bloomH3, color = colors.ink)
                    Text(text = lines[1], style = bloomBody, color = colors.inkSecondary)
                }
                else -> Text(text = block, style = bloomBody, color = colors.inkSecondary)
            }
        }
    }
}
