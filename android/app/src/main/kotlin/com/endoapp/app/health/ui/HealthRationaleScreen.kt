package com.endoapp.app.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.HealthConnectCopy
import com.endoapp.app.designsystem.BloomPrimaryButton
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH2

/**
 * Screen A — the rationale-BEFORE-permission screen (AHC-02). This is BOTH the in-app screen shown
 * before the system Health Connect permission sheet AND the manifest rationale target a Play reviewer
 * opens (via [PermissionsRationaleActivity]); it must render the SAME read-only privacy content in
 * both entries.
 *
 * Bloom layout: headline, a card holding the three plain lines (read-only / private / optional) + the
 * "what it reads" line, then a primary "Continue" (the only path to the system sheet) and a "Not now"
 * dismiss. All copy is read off [HealthConnectCopy] (the registered, CopyAudit-clean surface).
 *
 * [onContinue] / [onDismiss] default to no-ops so Plan 04 wires the permission launcher onto
 * "Continue"; when hosted by the rationale Activity, "Not now" finishes it. Imports NO
 * notification / WorkManager / alarm API (the flare-notification separation invariant).
 */
@Composable
fun HealthRationaleScreen(
    modifier: Modifier = Modifier,
    onContinue: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val colors = LocalBloomColors.current

    Column(
        modifier = modifier
            .testTag("hc.rationale.root")
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = HealthConnectCopy.RATIONALE_HEADLINE,
            style = bloomH2,
            color = colors.ink,
            modifier = Modifier.testTag("hc.rationale.headline"),
        )

        Column(
            modifier = Modifier.bloomCard(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = HealthConnectCopy.RATIONALE_READ_ONLY,
                style = bloomBody,
                color = colors.ink,
            )
            Text(
                text = HealthConnectCopy.RATIONALE_PRIVATE,
                style = bloomBody,
                color = colors.inkSecondary,
            )
            Text(
                text = HealthConnectCopy.RATIONALE_OPTIONAL,
                style = bloomBody,
                color = colors.inkSecondary,
            )
            Text(
                text = HealthConnectCopy.RATIONALE_WHAT_IT_READS,
                style = bloomBody,
                color = colors.inkSecondary,
            )
        }

        BloomPrimaryButton(
            text = HealthConnectCopy.CONTINUE,
            onClick = onContinue,
            modifier = Modifier.testTag("hc.rationale.continue"),
        )

        Text(
            text = HealthConnectCopy.NOT_NOW,
            style = bloomButtonLabel,
            color = colors.plum,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag("hc.rationale.dismiss")
                .fillMaxWidth()
                .clickable(onClick = onDismiss)
                .padding(vertical = 12.dp),
        )
    }
}
