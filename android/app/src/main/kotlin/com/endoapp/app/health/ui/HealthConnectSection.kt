// The "Connect Health data" Settings entry + the honest availability rendering (Phase 21, Plan 04;
// AHC-01) — the Kotlin analog of the iOS `HealthConnectSection.swift`. A passive Bloom row: tapping it
// resolves `getSdkStatus()` PER SESSION (never cached) and routes to the matching honest surface.
// Manual logging is NEVER gated behind this — it is just another Settings row.
//
// SEPARATION INVARIANT (T-20-02d): this file imports NO notification / alarm / WorkManager API.
package com.endoapp.app.health.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.HealthConnectCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.health.HealthAvailability
import com.endoapp.app.health.HealthConnectController
import kotlinx.coroutines.launch

/** The Health Connect provider package — the target of the Google Play deep link. */
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * The Settings "Connect Health data" section. Renders the entry row (label + caption) off
 * [HealthConnectCopy]; on tap it resolves [HealthConnectController.availability] for the session and:
 *
 * - **AVAILABLE** → invokes [onConnect] (the host opens [HealthImportScreen] — the rationale→import flow).
 * - **PROVIDER_UPDATE_REQUIRED** → reveals the honest install line + a "Get Health Connect on Google
 *   Play" button firing [onGetOnPlay] (the Play deep link). `getSdkStatus` cannot distinguish
 *   provider-absent from provider-stale, so the honest install variant is shown as the default.
 * - **UNAVAILABLE** → reveals the softened "not available on this device — manual logging works the
 *   same" line, no action (read as normal, NOT an error).
 *
 * The entry is inert until tapped and never blocks or hides manual logging.
 *
 * @param onConnect the AVAILABLE happy-path callback the host wires to the import screen.
 * @param onGetOnPlay the Play deep-link action (defaults to a real market/web launcher).
 */
@Composable
fun HealthConnectSection(
    controller: HealthConnectController,
    modifier: Modifier = Modifier,
    onConnect: () -> Unit = {},
    onGetOnPlay: () -> Unit = rememberOpenPlayForHealthConnect(),
) {
    val colors = LocalBloomColors.current
    val scope = rememberCoroutineScope()

    // The availability resolved on the LAST tap (null = not yet tapped). Re-resolved every tap (session).
    var resolved by remember { mutableStateOf<HealthAvailability?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bloomCard()
            .testTag("settings.healthConnectSlot"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The connect entry — the AVAILABLE happy path. Tapping resolves availability for the session.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable {
                    scope.launch {
                        val availability = controller.availability()
                        resolved = availability
                        if (availability == HealthAvailability.AVAILABLE) onConnect()
                    }
                }
                .testTag("hc.section.connect")
                .semantics { role = Role.Button; contentDescription = HealthConnectCopy.ENTRY_LABEL },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = HealthConnectCopy.ENTRY_LABEL, style = bloomBody, color = colors.ink)
            Text(text = HealthConnectCopy.ENTRY_CAPTION, style = bloomBody, color = colors.inkSecondary)
        }

        when (resolved) {
            HealthAvailability.PROVIDER_UPDATE_REQUIRED -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hc.section.updateRequired"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = HealthConnectCopy.UNAVAILABLE_NOT_INSTALLED,
                        style = bloomBody,
                        color = colors.inkSecondary,
                    )
                    Text(
                        text = HealthConnectCopy.GET_ON_PLAY,
                        style = bloomButtonLabel,
                        color = colors.plum,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 48.dp)
                            .clickable(onClick = onGetOnPlay)
                            .testTag("hc.section.getOnPlay")
                            .semantics { role = Role.Button; contentDescription = HealthConnectCopy.GET_ON_PLAY },
                    )
                }
            }

            HealthAvailability.UNAVAILABLE -> {
                Text(
                    text = HealthConnectCopy.UNAVAILABLE_IMPOSSIBLE,
                    style = bloomBody,
                    color = colors.inkSecondary,
                    modifier = Modifier.fillMaxWidth().testTag("hc.section.unavailable"),
                )
            }

            // AVAILABLE routes via onConnect (no inline surface); null = not yet tapped.
            else -> Unit
        }
    }
}

/**
 * The real Google Play deep-link for the Health Connect provider — opens the `market://` details page,
 * falling back to the `https://play.google.com` web URL when no store app handles the intent. Imports
 * no notification/alarm API; injected so the section stays provable off a fake in tests.
 */
@Composable
fun rememberOpenPlayForHealthConnect(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(market)
            } catch (_: ActivityNotFoundException) {
                val web = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(web)
            }
        }
    }
}
