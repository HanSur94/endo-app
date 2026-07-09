// The deliberate export/import Bloom surface (Phase 23-05, APRT-01/02) — the passphrase UX + the honest
// cannot-recover warning + the KDF spinner + the three gentle errors + the two success lines, driven
// ENTIRELY off seams so the whole screen is Robolectric/Compose-testable with NO real document picker.
//
// The screen owns only the passphrase text; the picker Uri never round-trips through it. Export/import
// each hand the entered passphrase (String -> CharArray at the boundary, T-23-10b) to a host seam that
// owns the SAF launcher and forwards the picked Uri + passphrase to [ExportImportViewModel]. The
// observable [ExportImportUiState] drives the spinner/success/error surfaces off registered
// [ExportImportCopy] strings — no raw error text, no exception ever reaches this layer.
//
// SEPARATION INVARIANT (T-23-09): this file imports NO notification / alarm / WorkManager API; the
// reminders package stays the app's SOLE notification owner.
package com.endoapp.app.export.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ExportImportCopy
import com.endoapp.app.content.SettingsCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH2
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.app.export.ExportImportUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * The "Your data" export/import Bloom screen, composed off seams:
 *
 * - [state] the live [ExportImportUiState] the ViewModel publishes (collected here). [Working] shows the
 *   indeterminate KDF spinner + the in-progress message; [Success]/[Error] render their ONE registered
 *   [ExportImportCopy] line — never an exception.
 * - [onPickExportDestination] the export seam: invoked with the entered passphrase (as a fresh
 *   [CharArray]) once the two fields match and are non-blank; the host launches `ACTION_CREATE_DOCUMENT`
 *   and, on the picked `Uri`, runs the export. The export button is disabled until the fields match.
 * - [onPickImportSource] the import seam: invoked with the entered passphrase; the host launches
 *   `ACTION_OPEN_DOCUMENT` and, on the picked `Uri`, runs the import.
 * - [onClose] pops the screen off the in-Log stack.
 *
 * The cannot-recover warning ([ExportImportCopy.CANNOT_RECOVER], testTag `export.warning`) is ALWAYS
 * visible under the export fields — the honest "a lost passphrase is an unopenable file" bar. The
 * passphrase lives as a `String` in Compose state ONLY, is converted to a [CharArray] at the seam
 * boundary, and the fields are cleared on submit so the plaintext is not retained (T-23-10b).
 */
@Composable
fun ExportImportScreen(
    state: StateFlow<ExportImportUiState>,
    modifier: Modifier = Modifier,
    onPickExportDestination: (CharArray) -> Unit = {},
    onPickImportSource: (CharArray) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val colors = LocalBloomColors.current
    val current by state.collectAsState()
    val working = current is ExportImportUiState.Working

    var choose by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var importPass by remember { mutableStateOf("") }

    val fieldsMatch = choose.isNotBlank() && choose == confirm
    val showMismatch = confirm.isNotBlank() && choose != confirm
    val canExport = fieldsMatch && !working
    val canImport = importPass.isNotBlank() && !working

    Column(
        modifier = modifier
            .testTag("exportImport.root")
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Back affordance (reuses the exempt Settings "Cancel" a11y label — no new copy).
        Text(
            text = "←",
            style = bloomH3,
            color = colors.ink,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(onClick = onClose)
                .testTag("exportImport.back")
                .semantics { role = Role.Button; contentDescription = SettingsCopy.CANCEL },
        )

        Text(
            text = ExportImportCopy.SECTION_HEADER,
            style = bloomH2,
            color = colors.ink,
            modifier = Modifier.testTag("exportImport.header"),
        )

        // --- Export sub-flow: choose + confirm + the always-visible cannot-recover warning ---
        Column(
            modifier = Modifier.fillMaxWidth().bloomCard(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = ExportImportCopy.EXPORT_LABEL, style = bloomH3, color = colors.ink)

            OutlinedTextField(
                value = choose,
                onValueChange = { choose = it },
                label = { Text(ExportImportCopy.CHOOSE_PASSPHRASE) },
                singleLine = true,
                enabled = !working,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .testTag("export.passphrase"),
            )

            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text(ExportImportCopy.CONFIRM_PASSPHRASE) },
                singleLine = true,
                enabled = !working,
                isError = showMismatch,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .testTag("export.passphraseConfirm"),
            )

            if (showMismatch) {
                Text(
                    text = ExportImportCopy.PASSPHRASE_MISMATCH,
                    style = bloomBody,
                    color = colors.roseDeep,
                    modifier = Modifier.testTag("export.mismatch"),
                )
            }

            // The honest cannot-be-recovered warning — ALWAYS visible (never gated on a state).
            Text(
                text = ExportImportCopy.CANNOT_RECOVER,
                style = bloomBody,
                color = colors.inkSecondary,
                modifier = Modifier.testTag("export.warning"),
            )

            ActionButton(
                label = ExportImportCopy.EXPORT_LABEL,
                enabled = canExport,
                testTag = "export.action",
                onClick = {
                    // String -> CharArray at the seam boundary; clear the plaintext fields after.
                    val secret = choose.toCharArray()
                    choose = ""
                    confirm = ""
                    onPickExportDestination(secret)
                },
            )
        }

        // --- Import sub-flow: passphrase + open-a-file action ---
        Column(
            modifier = Modifier.fillMaxWidth().bloomCard(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = ExportImportCopy.IMPORT_LABEL, style = bloomH3, color = colors.ink)

            OutlinedTextField(
                value = importPass,
                onValueChange = { importPass = it },
                label = { Text(ExportImportCopy.ENTER_PASSPHRASE) },
                singleLine = true,
                enabled = !working,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .testTag("import.passphrase"),
            )

            ActionButton(
                label = ExportImportCopy.IMPORT_LABEL,
                enabled = canImport,
                testTag = "import.action",
                onClick = {
                    val secret = importPass.toCharArray()
                    importPass = ""
                    onPickImportSource(secret)
                },
            )
        }

        // --- The single status surface: spinner (Working) / confirmation (Success) / calm banner (Error) ---
        when (val s = current) {
            ExportImportUiState.Idle -> Unit

            is ExportImportUiState.Working -> Row(
                modifier = Modifier.fillMaxWidth().bloomCard(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    color = colors.plum,
                    modifier = Modifier.size(20.dp).testTag("exportImport.spinner"),
                )
                Text(
                    text = s.message,
                    style = bloomBody,
                    color = colors.ink,
                    modifier = Modifier.testTag("exportImport.status"),
                )
            }

            is ExportImportUiState.Success -> StatusBanner(message = s.message, color = colors.ink)

            is ExportImportUiState.Error -> StatusBanner(message = s.message, color = colors.roseDeep)
        }
    }
}

/** A Bloom-styled ≥48dp action button whose disabled state is reflected in semantics (testable). */
@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = LocalBloomColors.current
    Text(
        text = label,
        style = bloomButtonLabel,
        color = if (enabled) colors.plum else colors.inkMuted,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
            .semantics { role = Role.Button; contentDescription = label },
    )
}

/** The calm success/error banner — a single registered line, tagged `exportImport.status`. */
@Composable
private fun StatusBanner(message: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = message,
        style = bloomBody,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .bloomCard()
            .testTag("exportImport.status"),
    )
}
