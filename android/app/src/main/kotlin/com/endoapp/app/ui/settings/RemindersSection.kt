// EndoApp (:app) — the Settings "Reminders" section (ATRK-04, Plan 20-05). Fills the plan-04 reserved
// slot at the TOP of Settings. This is the ONE place in the UI that requests `POST_NOTIFICATIONS`, and
// it does so JUST-IN-TIME — only in the master-toggle ENABLE handler, never at launch/onStart.
//
// SEPARATION NOTE: this file is the single UI touch point of the reminders package
// (`com.endoapp.app.reminders`). `NotificationSeparationTest` allowlists it alongside that package; NO
// other screen references a notification/alarm/POST_NOTIFICATIONS API. It contains NO LaunchedEffect /
// launch-time side effect, so the permission request can only fire from a user toggle (structurally).
package com.endoapp.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ReminderCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomEyebrow
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.app.reminders.ReminderController
import com.endoapp.app.reminders.ReminderPrefs
import kotlinx.coroutines.launch

/**
 * The Settings reminders section. Renders off the live [ReminderController.prefs] (or a default OFF
 * state when [controller] is null / not yet wired). The master toggle's ENABLE handler is the ONLY
 * caller of [requestNotificationPermission] (JIT `POST_NOTIFICATIONS`); on grant it seeds a default
 * time + schedules (via the controller), on denial it leaves the toggle OFF with calm copy + an
 * Open-Settings escape. The app stays fully usable regardless — logging is never gated on reminders.
 *
 * @param requestNotificationPermission the Android-13+ runtime request seam (defaults to a real
 *   `RequestPermission` launcher). Injected so the JIT behavior is provable off a fake.
 * @param openAppNotificationSettings deep-links to the OS notification settings (denied-state escape).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSection(
    controller: ReminderController?,
    modifier: Modifier = Modifier,
    requestNotificationPermission: ((Boolean) -> Unit) -> Unit = rememberNotificationPermissionRequester(),
    openAppNotificationSettings: () -> Unit = rememberOpenNotificationSettings(),
) {
    val colors = LocalBloomColors.current
    val scope = rememberCoroutineScope()
    val prefs by (controller?.prefs?.collectAsState(initial = ReminderPrefs())
        ?: remember { mutableStateOf(ReminderPrefs()) })

    // Denied is transient UI state (Android hides the actual grant status), reset on any toggle change.
    var denied by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimeTarget?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bloomCard()
            .testTag("settings.remindersSlot"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = ReminderCopy.REMINDERS, style = bloomEyebrow, color = colors.inkSecondary)

        // Master toggle — the ONLY POST_NOTIFICATIONS request site (JIT, enable handler only).
        Row(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = ReminderCopy.DAILY_REMINDERS,
                style = bloomBody,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = prefs.enabled,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        // JIT permission: Android 13+ needs a runtime grant; below 13 it is implicit.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestNotificationPermission { granted ->
                                if (granted) {
                                    denied = false
                                    scope.launch { controller?.enable() }
                                } else {
                                    denied = true // leave the toggle OFF, calm copy + Open Settings.
                                }
                            }
                        } else {
                            denied = false
                            scope.launch { controller?.enable() }
                        }
                    } else {
                        denied = false
                        scope.launch { controller?.disable() }
                    }
                },
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("reminders.toggle")
                    .semantics { role = Role.Switch; contentDescription = ReminderCopy.DAILY_REMINDERS },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.card,
                    checkedTrackColor = colors.plum,
                ),
            )
        }

        when {
            denied -> {
                Text(text = ReminderCopy.DENIED, style = bloomBody, color = colors.inkSecondary)
                Text(
                    text = ReminderCopy.OPEN_SETTINGS,
                    style = bloomButtonLabel,
                    color = colors.plum,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clickable(onClick = openAppNotificationSettings)
                        .testTag("reminders.openSettings")
                        .semantics { role = Role.Button; contentDescription = ReminderCopy.OPEN_SETTINGS },
                )
            }

            prefs.enabled -> {
                Text(text = ReminderCopy.REMINDERS_ON_FOOTER, style = bloomBody, color = colors.inkSecondary)

                val time = prefs.times.firstOrNull() ?: ReminderPrefs.DEFAULT_TIME
                TimeRow(
                    label = ReminderCopy.REMINDER_TIME,
                    minute = time,
                    testTag = "reminders.time",
                    onClick = { editing = TimeTarget.Reminder },
                )

                // Quiet hours toggle + (when on) the from/until pickers.
                Row(
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = ReminderCopy.QUIET_HOURS,
                        style = bloomBody,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = prefs.quietHoursEnabled,
                        onCheckedChange = { on ->
                            scope.launch {
                                controller?.setQuietHours(on, prefs.quietStart, prefs.quietEnd)
                            }
                        },
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("reminders.quietToggle")
                            .semantics { role = Role.Switch; contentDescription = ReminderCopy.QUIET_HOURS },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.card,
                            checkedTrackColor = colors.plum,
                        ),
                    )
                }
                if (prefs.quietHoursEnabled) {
                    TimeRow(
                        label = ReminderCopy.QUIET_HOURS_FROM,
                        minute = prefs.quietStart,
                        testTag = "reminders.quietFrom",
                        onClick = { editing = TimeTarget.QuietStart },
                    )
                    TimeRow(
                        label = ReminderCopy.QUIET_HOURS_UNTIL,
                        minute = prefs.quietEnd,
                        testTag = "reminders.quietUntil",
                        onClick = { editing = TimeTarget.QuietEnd },
                    )
                }
            }

            else -> {
                Text(text = ReminderCopy.REMINDERS_OFF_FOOTER, style = bloomBody, color = colors.inkSecondary)
            }
        }
    }

    // The time picker — opened for whichever row is being edited. Every confirm re-schedules idempotently.
    editing?.let { target ->
        val initial = when (target) {
            TimeTarget.Reminder -> prefs.times.firstOrNull() ?: ReminderPrefs.DEFAULT_TIME
            TimeTarget.QuietStart -> prefs.quietStart
            TimeTarget.QuietEnd -> prefs.quietEnd
        }
        val state = rememberTimePickerState(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editing = null },
            modifier = Modifier.testTag("reminders.timePicker"),
            confirmButton = {
                TextButton(
                    onClick = {
                        val minute = state.hour * 60 + state.minute
                        scope.launch {
                            when (target) {
                                TimeTarget.Reminder -> controller?.setTime(minute)
                                TimeTarget.QuietStart ->
                                    controller?.setQuietHours(true, minute, prefs.quietEnd)
                                TimeTarget.QuietEnd ->
                                    controller?.setQuietHours(true, prefs.quietStart, minute)
                            }
                        }
                        editing = null
                    },
                    modifier = Modifier.testTag("reminders.timeConfirm"),
                ) { Text(ReminderCopy.ADD_A_TIME, style = bloomButtonLabel, color = colors.plum) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text("Cancel", style = bloomButtonLabel, color = colors.inkMuted)
                }
            },
            title = { Text(ReminderCopy.REMINDER_TIME, style = bloomH3, color = colors.ink) },
            text = { TimePicker(state = state) },
            containerColor = colors.card,
        )
    }
}

/** Which time value a picker edit targets. */
private enum class TimeTarget { Reminder, QuietStart, QuietEnd }

/** A ≥48dp labelled time row — the label + the formatted HH:mm, opening a picker on tap. */
@Composable
private fun TimeRow(label: String, minute: Int, testTag: String, onClick: () -> Unit) {
    val colors = LocalBloomColors.current
    val formatted = "%02d:%02d".format(minute / 60, minute % 60)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics { role = Role.Button; contentDescription = "$label $formatted" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = bloomBody, color = colors.ink)
        Text(text = formatted, style = bloomBody, color = colors.inkSecondary)
    }
}

/**
 * The real just-in-time `POST_NOTIFICATIONS` requester — a `RequestPermission` launcher registered
 * ONCE (registration is not a request), returning a `(onResult) -> Unit` the enable handler invokes.
 * This is the only production path that touches the runtime permission.
 */
@Composable
fun rememberNotificationPermissionRequester(): ((Boolean) -> Unit) -> Unit {
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pending?.invoke(granted)
        pending = null
    }
    return remember(launcher) {
        { onResult ->
            pending = onResult
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Deep-links to the app's OS notification settings (the denied-state escape). */
@Composable
fun rememberOpenNotificationSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
