// EndoApp (:app) — the Settings surface (ATRK-03 / ATRK-05, Plan 20-04). Reached from the Log-home gear
// via the intra-Log stack. Hosts, top-to-bottom:
//   1. a RESERVED reminders slot (an empty, clearly-marked composable for Plan 05 to fill — this file
//      imports NO notification / WorkManager / alarm API, keeping the reminder surface cleanly separable);
//   2. the manage-meds section (add + soft-archive);
//   3. a cycle-tracking-off toggle bound to the standing TreatmentState (suppressed / naturallyCycling);
//   4. a Privacy row opening the Phase-19 PrivacyPolicyScreen;
//   5. a destructive Withdraw-consent control (the graduated Phase-19 temp shell affordance) behind a
//      confirmation dialog that calls the real onWithdraw → ConsentStore.withdraw → the app re-gates.
//
// Out of scope this phase (UI-SPEC §1): NO Health Connect / iCloud / export sections.
package com.endoapp.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.SettingsCopy
import com.endoapp.app.content.ShellCopy
import com.endoapp.app.content.WithdrawConsentCopy
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.app.health.HealthConnectController
import com.endoapp.app.health.ui.HealthConnectSection
import com.endoapp.app.reminders.ReminderController
import com.endoapp.app.ui.onboarding.PrivacyPolicyScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * The Settings screen.
 *
 * @param onWithdraw the graduated withdraw-consent action → `ConsentStore.withdraw` (re-gates the app).
 * @param onBack pops Settings off the in-Log stack.
 * @param medications the live active saved-med list off the consent-gated repository.
 * @param onAddMedication / onArchiveMedication the consent-gated med write seams (add / soft-archive).
 * @param loadSuppressed reads the latest TreatmentState's suppressed flag for the toggle's initial value.
 * @param onSetSuppressed writes a fresh timestamped TreatmentState (latest wins) on toggle.
 * @param reminders the daily-reminder facade the reminders section drives (null when not yet wired /
 *   in isolation — the section then renders a default OFF state and stays inert).
 * @param healthConnect the Health Connect connect/import seam (null when not yet wired / in isolation —
 *   the connect section is then omitted entirely, keeping Settings inert; the exact reminders posture).
 * @param onConnectHealth the AVAILABLE happy-path callback the host wires to the Health Connect import
 *   screen (the composition-root wiring lands in Plan 05).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onWithdraw: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    reminders: ReminderController? = null,
    healthConnect: HealthConnectController? = null,
    onConnectHealth: () -> Unit = {},
    medications: Flow<List<Medication>> = emptyFlow(),
    onAddMedication: suspend (Medication) -> Unit = {},
    onArchiveMedication: suspend (Medication) -> Unit = {},
    loadSuppressed: suspend () -> Boolean = { false },
    onSetSuppressed: suspend (Boolean) -> Unit = {},
) {
    val colors = LocalBloomColors.current
    val scope = rememberCoroutineScope()
    val meds by medications.collectAsState(initial = emptyList())

    var suppressed by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }

    // Read the standing treatment state once so the toggle reflects the persisted suppression.
    LaunchedEffect(Unit) { suppressed = loadSuppressed() }

    Scaffold(
        modifier = modifier.testTag("log.route.settings"),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text(SettingsCopy.TITLE, style = bloomH3, color = colors.ink) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("settings.back")
                            .semantics { contentDescription = SettingsCopy.CANCEL },
                    ) { Text("←", style = bloomH3, color = colors.ink) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.canvas),
            )
        },
        containerColor = colors.canvas,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // (1) Reminders — the daily check-in reminder (Plan 05). The ONLY UI touch point of the
            // reminders package; the master toggle is the sole JIT POST_NOTIFICATIONS request site.
            RemindersSection(controller = reminders)

            // (2) Manage meds.
            ManageMedsSection(
                medications = meds,
                onAddMedication = { med -> scope.launch { onAddMedication(med) } },
                onArchiveMedication = { med -> scope.launch { onArchiveMedication(med) } },
            )

            // (3) Cycle tracking off (treatment / suppression state).
            CycleOffCard(
                on = suppressed,
                onToggle = { next ->
                    suppressed = next
                    scope.launch {
                        onSetSuppressed(next)
                        // Persist a fresh timestamped TreatmentState (latest wins) via the seam below.
                    }
                },
            )

            // (3b) Connect Health data — the availability-gated Health Connect entry (Plan 04). Omitted
            // entirely when no controller is threaded (isolation / not yet wired), so Settings stays
            // inert; NEVER gates manual logging in any availability state.
            if (healthConnect != null) {
                HealthConnectSection(controller = healthConnect, onConnect = onConnectHealth)
            }

            // (4) Privacy → the Phase-19 PrivacyPolicyScreen.
            SettingsRow(label = SettingsCopy.PRIVACY, testTag = "settings.privacy") { showPrivacy = true }

            // (5) Withdraw consent (destructive; graduated from the Phase-19 temp shell affordance).
            WithdrawCard { showWithdraw = true }
        }
    }

    if (showWithdraw) {
        AlertDialog(
            onDismissRequest = { showWithdraw = false },
            modifier = Modifier.testTag("settings.withdrawDialog"),
            title = { Text(WithdrawConsentCopy.TITLE, style = bloomH3, color = colors.ink) },
            text = { Text(WithdrawConsentCopy.BODY, style = bloomBody, color = colors.inkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWithdraw = false
                        onWithdraw()
                    },
                    modifier = Modifier.testTag("settings.withdrawConfirm"),
                ) { Text(ShellCopy.WITHDRAW, style = bloomButtonLabel, color = colors.rose) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWithdraw = false },
                    modifier = Modifier.testTag("settings.withdrawCancel"),
                ) { Text(WithdrawConsentCopy.KEEP, style = bloomButtonLabel, color = colors.inkMuted) }
            },
            containerColor = colors.card,
        )
    }

    // The Privacy policy overlay — composed in the SAME composition (it owns its own back handler),
    // drawing over Settings until dismissed.
    if (showPrivacy) {
        Box(modifier = Modifier.fillMaxSize()) {
            PrivacyPolicyScreen(onDismiss = { showPrivacy = false })
        }
    }
}

/** The cycle-tracking-off card — a `Role.Switch` bound to the suppression state + the honest footer. */
@Composable
private fun CycleOffCard(on: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalBloomColors.current
    Column(
        modifier = Modifier.fillMaxWidth().bloomCard(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = SettingsCopy.CYCLE_OFF, style = bloomBody, color = colors.ink, modifier = Modifier.weight(1f))
            Switch(
                checked = on,
                onCheckedChange = onToggle,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("settings.cycleOff")
                    .semantics { role = Role.Switch; contentDescription = SettingsCopy.CYCLE_OFF },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.card,
                    checkedTrackColor = colors.plum,
                ),
            )
        }
        Text(text = SettingsCopy.CYCLE_OFF_FOOTER, style = bloomBody, color = colors.inkSecondary)
    }
}

/** A generic tappable Settings row (Privacy) — a ≥48dp `Role.Button` label + a trailing chevron. */
@Composable
private fun SettingsRow(label: String, testTag: String, onClick: () -> Unit) {
    val colors = LocalBloomColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bloomCard()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics { role = Role.Button; contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = bloomBody, color = colors.ink)
        Text(text = "›", style = bloomH3, color = colors.inkMuted)
    }
}

/** The destructive withdraw-consent card — opens the confirmation dialog. */
@Composable
private fun WithdrawCard(onClick: () -> Unit) {
    val colors = LocalBloomColors.current
    Text(
        text = ShellCopy.WITHDRAW,
        style = bloomButtonLabel,
        color = colors.rose,
        modifier = Modifier
            .fillMaxWidth()
            .bloomCard()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .testTag("settings.withdraw")
            .semantics { role = Role.Button; contentDescription = ShellCopy.WITHDRAW },
    )
}
