// EndoApp (:app) — the add-medication form (ATRK-03). A Bloom `ModalBottomSheet` with a required Name
// field + an optional Type/note field, Cancel / Save (Save disabled until the name is non-blank). On
// Save it hands a fresh [Medication] DEFINITION (a generated id, `isArchived = null` = active) up to the
// caller, which writes it through the consent-gated [EndoRepository] (where the free-text length is
// bounded — never here in the UI, per UI-SPEC §Section-row contracts).
package com.endoapp.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.SettingsCopy
import com.endoapp.app.designsystem.EyebrowLabel
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.data.entities.Medication
import java.util.UUID

/**
 * The add-medication bottom sheet.
 *
 * @param onSave receives a fresh [Medication] definition (generated id, active) to persist through the
 *   consent-gated repository. Called once, then the sheet dismisses.
 * @param onDismiss dismisses the sheet (Cancel / scrim / back).
 * @param now the creation timestamp for the new definition (injectable for tests).
 * @param idFor generates the new definition's stable id (injectable for tests).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationSheet(
    onSave: (Medication) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    idFor: () -> String = { UUID.randomUUID().toString() },
) {
    val colors = LocalBloomColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val canSave = name.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("settings.addMed.sheet"),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EyebrowLabel(SettingsCopy.ADD_MED)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(SettingsCopy.MED_NAME) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .testTag("settings.addMed.name"),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(SettingsCopy.MED_NOTE) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .testTag("settings.addMed.note"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = androidx.compose.ui.Alignment.End),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .testTag("settings.addMed.cancel"),
                ) { Text(SettingsCopy.CANCEL, style = bloomBody, color = colors.inkMuted) }

                TextButton(
                    onClick = {
                        onSave(
                            Medication(
                                id = idFor(),
                                name = name.trim(),
                                note = note.trim().ifBlank { null },
                                createdAtMillis = now,
                                isArchived = null,
                            ),
                        )
                        onDismiss()
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .testTag("settings.addMed.save"),
                ) { Text(SettingsCopy.SAVE, style = bloomButtonLabel, color = if (canSave) colors.plum else colors.inkMuted) }
            }
        }
    }
}
