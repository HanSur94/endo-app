package com.endoapp.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.endoapp.app.designsystem.BloomTheme

/** The reminder deep-link extra a Phase-20 notification sets; here it only flips the router flag. */
const val EXTRA_REMINDER_CHECK_IN: String = "com.endoapp.app.extra.REMINDER_CHECK_IN"

/**
 * The single activity hosting the entire app: `enableEdgeToEdge()` then `BloomTheme { AppGate() }`.
 * The whole routing surface is the consent-by-construction [AppGate]; this activity adds no
 * navigation of its own.
 *
 * A launch/new intent carrying [EXTRA_REMINDER_CHECK_IN] only flips the non-sensitive
 * [CheckInRouter.pendingCheckIn] flag — which [AppGate] observes ONLY inside its `Consented` arm — so
 * a deep link/notification lands on the gate and can never bypass it (threat T-19-01b).
 */
class MainActivity : ComponentActivity() {

    private val dependencies: AppDependencies
        get() = (application as App).dependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.hasReminderCheckIn()) {
            dependencies.checkInRouter.pendingCheckIn = true
        }
        setContent {
            BloomTheme {
                AppGate(
                    consentStore = dependencies.consentStore,
                    router = dependencies.checkInRouter,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasReminderCheckIn()) {
            dependencies.checkInRouter.pendingCheckIn = true
        }
    }
}

private fun Intent.hasReminderCheckIn(): Boolean =
    getBooleanExtra(EXTRA_REMINDER_CHECK_IN, false)
