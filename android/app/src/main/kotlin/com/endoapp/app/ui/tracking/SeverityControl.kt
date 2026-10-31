// EndoApp (:app) — the shared 0–10 discrete severity control (SYMP-01 / D-02), reused by Check-In
// (Symptoms, Stress) and Day Log (Symptoms, Pain-area sheet). Port of the iOS `SeverityControl`.
//
// A labelled discrete `Slider` (step 1 over 0..10) with a "Severity N/10" readout and Mild/Moderate/
// Severe preset chips. Accessibility: the slider carries progress/`Role.Slider` semantics + a
// `stateDescription` "N of 10"; presets are `Role.Button` with a selected state; every target ≥48dp.
// All strings come from [TrackingCopy] (registered + audit-clean). No health value is fabricated —
// the caller owns the draft value and its nil-vs-set state.
package com.endoapp.app.ui.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomChipLabel

/** The severity a preset chip selects (Mild = 3, Moderate = 6, Severe = 9). */
private const val MILD = 3
private const val MODERATE = 6
private const val SEVERE = 9

/**
 * The shared severity picker. [value] is the current 0..10 severity (a real logged value — the
 * caller decides whether the control is shown at all, honouring absence ≠ 0); [onValueChange] is
 * fired with the new discrete value.
 *
 * testTags: the slider is `daylog.severitySlider`; presets are `daylog.severityPreset.<mild|
 * moderate|severe>` (mirrors the iOS a11y ids).
 */
@Composable
fun SeverityControl(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Label + "N/10" readout.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = TrackingCopy.SEVERITY, style = bloomBody, color = colors.ink)
            Text(text = TrackingCopy.severityReadout(value), style = bloomBody, color = colors.inkSecondary)
        }

        // Discrete 0..10 slider (11 stops → steps = 9), rose-tinted, with a "N of 10" stateDescription.
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = colors.rose,
                activeTrackColor = colors.rose,
                inactiveTrackColor = colors.surfaceSunken,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .testTag("daylog.severitySlider")
                .semantics { stateDescription = TrackingCopy.severityStateDescription(value) },
        )

        // Preset chips.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityPreset("mild", TrackingCopy.SEVERITY_PRESET_MILD, MILD, value, onValueChange)
            SeverityPreset("moderate", TrackingCopy.SEVERITY_PRESET_MODERATE, MODERATE, value, onValueChange)
            SeverityPreset("severe", TrackingCopy.SEVERITY_PRESET_SEVERE, SEVERE, value, onValueChange)
        }
    }
}

/** One preset chip — a ≥48dp `Role.Button` selecting [preset]; selected when the current [value] matches. */
@Composable
private fun SeverityPreset(
    key: String,
    label: String,
    preset: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val colors = LocalBloomColors.current
    val selected = value == preset
    Text(
        text = label,
        style = bloomChipLabel,
        color = if (selected) colors.plum else colors.inkMuted,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable { onValueChange(preset) }
            .testTag("daylog.severityPreset.$key")
            .semantics {
                role = Role.Button
                this.selected = selected
            },
    )
}
