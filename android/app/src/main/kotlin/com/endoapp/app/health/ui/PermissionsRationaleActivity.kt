package com.endoapp.app.health.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.endoapp.app.designsystem.BloomTheme

/**
 * The manifest rationale target (AHC-02). BOTH Health Connect permission-rationale declarations point
 * here:
 *   - the Android 13- rationale `<activity>` (`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`), and
 *   - the Android 14+ `<activity-alias>` (`VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS`, guarded by
 *     `START_VIEW_PERMISSION_USAGE`).
 * A Play reviewer (or a user tapping the app's entry in the Health Connect permission manager) opens
 * this Activity; it MUST render the SAME read-only privacy content the in-app connect flow shows —
 * hence it hosts the shared [HealthRationaleScreen].
 *
 * Mirrors `MainActivity`'s host pattern (`enableEdgeToEdge()` then `BloomTheme { … }`). It imports NO
 * notification / WorkManager / alarm API — the flare-notification separation invariant. "Not now"
 * simply finishes the Activity (this is a standalone rationale surface, not the permission launcher;
 * Plan 04 wires the launcher onto the in-app rationale entry).
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BloomTheme {
                HealthRationaleScreen(onDismiss = { finish() })
            }
        }
    }
}
