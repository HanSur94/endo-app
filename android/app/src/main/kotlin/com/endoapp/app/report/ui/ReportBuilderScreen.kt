// The "Export report" builder Bloom surface (Phase 24-05, AREL-01/02) — the range picker (presets +
// custom with the inline invalid-range guard) → Generate → a confirmation with the Share action, driven
// ENTIRELY off seams so the whole flow is Robolectric/Compose-testable with NO real OS chooser and NO
// real PDF (mirrors the Phase-23 `ExportImportScreen`).
//
// CONFIRMATION, NOT A BITMAP PREVIEW (RESEARCH Open Question 3): on [ReportPhase.ReadyToShare] the screen
// shows a plain confirmation (title + covered range + Share) rather than a `PdfRenderer` bitmap preview —
// keeping the flow fully JVM-testable off seams. A rendered bitmap preview is a device-gated later nicety.
//
// COPY DISCIPLINE (T-24-03): EVERY visible string comes from the registered `ReportCopy` — no inline
// literal. SEPARATION / NO-NETWORK: this file imports NO notification / alarm / WorkManager / networking
// API (the report package stays network-free; the reminders package stays the sole notification owner).
package com.endoapp.app.report.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ReportCopy
import com.endoapp.app.content.SettingsCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH2
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.app.report.RangePreset
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The report builder screen, composed off seams:
 *
 * - [state] the live [ReportBuilderUiState] the ViewModel publishes (collected here). Its [preset] +
 *   [ReportBuilderUiState.customFrom]/[ReportBuilderUiState.customTo] drive the picker; its
 *   [ReportBuilderUiState.phase] drives the Generate/spinner/Share/error surface.
 * - [onSelectPreset] pick a range preset (LAST30 / LAST90 / CUSTOM).
 * - [onSetCustomRange] adjust the custom From/To span (the ±1-day steppers).
 * - [onGenerate] assemble + render the report (invoked only when [ReportBuilderUiState.isGenerateEnabled]).
 * - [onShare] hand the produced PDF to the OS share chooser (the host renders `ReportShareHost`).
 * - [onClose] pops the builder off the in-Log stack.
 */
@Composable
fun ReportBuilderScreen(
    state: StateFlow<ReportBuilderUiState>,
    modifier: Modifier = Modifier,
    onSelectPreset: (RangePreset) -> Unit = {},
    onSetCustomRange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    onGenerate: () -> Unit = {},
    onShare: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val colors = LocalBloomColors.current
    val current by state.collectAsState()

    Column(
        modifier = modifier
            .testTag("report.root")
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
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
                .testTag("report.back")
                .semantics { role = Role.Button; contentDescription = SettingsCopy.CANCEL },
        )

        Text(
            text = ReportCopy.BUILDER_TITLE,
            style = bloomH2,
            color = colors.ink,
            modifier = Modifier.testTag("report.header"),
        )

        Text(
            text = ReportCopy.SETTINGS_ENTRY_FOOTER,
            style = bloomBody,
            color = colors.inkSecondary,
        )

        // --- Range picker: the three presets + (for CUSTOM) the From/To steppers ---
        Column(
            modifier = Modifier.fillMaxWidth().bloomCard(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PresetChip(ReportCopy.PRESET_LAST30, "report.preset.last30", current.preset == RangePreset.LAST30) {
                onSelectPreset(RangePreset.LAST30)
            }
            PresetChip(ReportCopy.PRESET_LAST90, "report.preset.last90", current.preset == RangePreset.LAST90) {
                onSelectPreset(RangePreset.LAST90)
            }
            PresetChip(ReportCopy.PRESET_CUSTOM, "report.preset.custom", current.preset == RangePreset.CUSTOM) {
                onSelectPreset(RangePreset.CUSTOM)
            }

            if (current.preset == RangePreset.CUSTOM) {
                DateStepperRow(
                    label = ReportCopy.RANGE_FROM_LABEL,
                    date = current.customFrom,
                    testTag = "report.range.from",
                    onChange = { onSetCustomRange(it, current.customTo) },
                )
                DateStepperRow(
                    label = ReportCopy.RANGE_TO_LABEL,
                    date = current.customTo,
                    testTag = "report.range.to",
                    onChange = { onSetCustomRange(current.customFrom, it) },
                )

                // The inline invalid-range guard (drives the disabled Generate below).
                if (!current.isRangeValid) {
                    Text(
                        text = ReportCopy.INVALID_RANGE,
                        style = bloomBody,
                        color = colors.roseDeep,
                        modifier = Modifier.testTag("report.invalidRange"),
                    )
                }
            }
        }

        // --- Generate (disabled on an invalid range or while generating) ---
        ActionButton(
            label = ReportCopy.GENERATE_CTA,
            enabled = current.isGenerateEnabled,
            testTag = "report.generate",
            onClick = onGenerate,
        )

        // --- The single status surface: spinner (Generating) / confirmation + Share (ReadyToShare) / error ---
        when (val phase = current.phase) {
            ReportPhase.Idle -> Unit

            ReportPhase.Generating -> Row(
                modifier = Modifier.fillMaxWidth().bloomCard(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    color = colors.plum,
                    modifier = Modifier.size(20.dp).testTag("report.spinner"),
                )
                Text(
                    text = ReportCopy.GENERATING_CAPTION,
                    style = bloomBody,
                    color = colors.ink,
                    modifier = Modifier.testTag("report.status"),
                )
            }

            is ReportPhase.ReadyToShare -> Column(
                modifier = Modifier.fillMaxWidth().bloomCard(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = ReportCopy.PDF_TITLE,
                    style = bloomH3,
                    color = colors.ink,
                    modifier = Modifier.testTag("report.status"),
                )
                ActionButton(
                    label = ReportCopy.SHARE_ACTION,
                    enabled = true,
                    testTag = "report.share",
                    onClick = onShare,
                )
            }

            ReportPhase.Error -> Column(
                modifier = Modifier.fillMaxWidth().bloomCard(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = ReportCopy.ERROR_HEADING,
                    style = bloomH3,
                    color = colors.roseDeep,
                    modifier = Modifier.testTag("report.status"),
                )
                Text(text = ReportCopy.ERROR_BODY, style = bloomBody, color = colors.inkSecondary)
            }
        }
    }
}

/** A date-formatter for the From/To display (locale-stable, day-granular). */
private val rangeDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** A selectable preset chip — a ≥48dp `Role.Button` whose selection is reflected in its ink weight. */
@Composable
private fun PresetChip(label: String, testTag: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalBloomColors.current
    Text(
        text = label,
        style = if (selected) bloomButtonLabel else bloomBody,
        color = if (selected) colors.plum else colors.ink,
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics {
                role = Role.Button
                contentDescription = label
                this.selected = selected
            },
    )
}

/** A From/To row: the label + the formatted date + ±1-day steppers. The row itself carries [testTag]. */
@Composable
private fun DateStepperRow(
    label: String,
    date: LocalDate,
    testTag: String,
    onChange: (LocalDate) -> Unit,
) {
    val colors = LocalBloomColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = bloomBody, color = colors.inkSecondary, modifier = Modifier.weight(1f))
        Stepper("−", "$testTag.minus") { onChange(date.minusDays(1)) }
        Text(
            text = date.format(rangeDateFormat),
            style = bloomBody,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.sizeIn(minWidth = 96.dp).testTag("$testTag.value"),
        )
        Stepper("+", "$testTag.plus") { onChange(date.plusDays(1)) }
    }
}

/** A ≥48dp square stepper glyph button. */
@Composable
private fun Stepper(glyph: String, testTag: String, onClick: () -> Unit) {
    val colors = LocalBloomColors.current
    Text(
        text = glyph,
        style = bloomH3,
        color = colors.plum,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics { role = Role.Button; contentDescription = glyph },
    )
}

/** A Bloom-styled ≥48dp action button whose disabled state is reflected in semantics (testable). */
@Composable
private fun ActionButton(label: String, enabled: Boolean, testTag: String, onClick: () -> Unit) {
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
