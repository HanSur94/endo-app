// EndoApp (:app) — the manage-medications section of Settings (ATRK-03). Renders the saved-med list
// (active only, off the consent-gated repository's `observeMedications`), an honest empty state, an
// "Add a medication" action opening the [AddMedicationSheet], and a per-med SOFT-archive behind a
// "Remove this medication?" confirmation. Archiving sets `isArchived = true` — NEVER a hard-delete —
// so the definition survives and any day that logged it as taken (a FactorReading keyed by the med id)
// keeps its meaning (threat T-20-05).
package com.endoapp.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.SettingsCopy
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.designsystem.EyebrowLabel
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH3

/**
 * The manage-meds Settings card.
 *
 * @param medications the active saved-medication list (already filtered of soft-archived defs).
 * @param onAddMedication persists a new [Medication] definition through the consent-gated repository.
 * @param onArchiveMedication soft-archives a definition (`copy(isArchived = true)`) — never a hard delete.
 */
@Composable
fun ManageMedsSection(
    medications: List<Medication>,
    onAddMedication: (Medication) -> Unit,
    onArchiveMedication: (Medication) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    var showAddSheet by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<Medication?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bloomCard()
            .testTag("settings.manageMeds"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EyebrowLabel(SettingsCopy.MANAGE_MEDS)

        if (medications.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("settings.meds.empty"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = SettingsCopy.NO_MEDS_TITLE, style = bloomH3, color = colors.ink)
                Text(text = SettingsCopy.NO_MEDS_BODY, style = bloomBody, color = colors.inkSecondary)
            }
        } else {
            medications.forEach { med -> MedRow(med) { pendingRemove = med } }
        }

        // Add-a-medication action.
        Text(
            text = SettingsCopy.ADD_MED,
            style = bloomButtonLabel,
            color = colors.plum,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .clickable { showAddSheet = true }
                .testTag("settings.addMed")
                .semantics { role = Role.Button; contentDescription = SettingsCopy.ADD_MED }
                .padding(vertical = 12.dp),
        )
    }

    if (showAddSheet) {
        AddMedicationSheet(
            onSave = { onAddMedication(it) },
            onDismiss = { showAddSheet = false },
        )
    }

    pendingRemove?.let { med ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            modifier = Modifier.testTag("settings.med.removeDialog"),
            title = { Text(SettingsCopy.REMOVE_MED_TITLE, style = bloomH3, color = colors.ink) },
            text = { Text(SettingsCopy.REMOVE_MED_BODY, style = bloomBody, color = colors.inkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onArchiveMedication(med.copy(isArchived = true)) // SOFT-archive; never hard-delete.
                        pendingRemove = null
                    },
                    modifier = Modifier.testTag("settings.med.removeConfirm"),
                ) { Text(SettingsCopy.REMOVE, style = bloomButtonLabel, color = colors.rose) }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRemove = null },
                    modifier = Modifier.testTag("settings.med.removeCancel"),
                ) { Text(SettingsCopy.CANCEL, style = bloomButtonLabel, color = colors.inkMuted) }
            },
            containerColor = colors.card,
        )
    }
}

/** One saved-med row — the name (or a neutral fallback) + a ≥48dp Remove button (opens the confirm). */
@Composable
private fun MedRow(med: Medication, onRemove: () -> Unit) {
    val colors = LocalBloomColors.current
    val label = med.name?.takeIf { it.isNotBlank() } ?: SettingsCopy.MED_NAME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .testTag("settings.med.${med.id}")
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = bloomBody, color = colors.ink)
            med.note?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = bloomBody, color = colors.inkSecondary)
            }
        }
        Text(
            text = SettingsCopy.REMOVE,
            style = bloomButtonLabel,
            color = colors.rose,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(onClick = onRemove)
                .testTag("settings.med.${med.id}.remove")
                .semantics { role = Role.Button; contentDescription = SettingsCopy.REMOVE }
                .padding(vertical = 12.dp, horizontal = 8.dp),
        )
    }
}
