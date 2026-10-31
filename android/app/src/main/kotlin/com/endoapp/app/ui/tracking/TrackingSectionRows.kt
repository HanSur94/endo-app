// EndoApp (:app) — the shared section-row primitives (UI-SPEC §Section-row component contracts),
// reused by Check-In (Plan 02) and Day Log (Plan 03). Each row is ≥48dp and carries the accessibility
// contract from UI-SPEC §6 (correct Role, selected/toggle state, value/"not logged" stateDescription).
// Every string comes from [TrackingCopy]; these primitives fabricate no health value — the caller
// owns the nil-honest draft and passes an absence-encoded state in.
package com.endoapp.app.ui.tracking

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody

/** Row height / minimum touch target shared by every section row (UI-SPEC §5). */
private val ROW_MIN = 48.dp

/**
 * A multi-select chip row (symptoms, food) — `Role.Checkbox` toggleable, a leading filled/outline
 * selection dot + a body label. [selected] reflects the checked state; [onToggle] flips it.
 */
@Composable
fun MultiSelectRow(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = ROW_MIN)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .testTag(testTag)
            .semantics { contentDescription = label }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionDot(selected)
        Text(text = label, style = bloomBody, color = colors.ink)
    }
}

/**
 * A single-select row (activity type, bleeding flow) — `Role.RadioButton`, a trailing plum tick when
 * selected. Re-tap clears is the CALLER's job (pass an [onClick] that sets nil when already selected).
 */
@Composable
fun SingleSelectRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = ROW_MIN)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag)
            .semantics { contentDescription = label }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = bloomBody, color = colors.ink)
        if (selected) Text(text = "✓", style = bloomBody, color = colors.plum)
    }
}

/**
 * A stepper (duration, sleep hours) — a label, a value read-out ("—" when unset), and minus/plus
 * buttons (each ≥48dp). The value node's `stateDescription` carries the value or "not logged"
 * ([valueText] `null` = not logged). [onDecrement]/[onIncrement] are the caller's ±step handlers.
 */
@Composable
fun StepperRow(
    label: String,
    valueText: String?,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = ROW_MIN)
            .testTag(testTag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = bloomBody, color = colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepperButton("−", "$testTag.decrement", onDecrement)
            Text(
                text = valueText ?: TrackingCopy.UNSET_READOUT,
                style = bloomBody,
                color = colors.inkSecondary,
                modifier = Modifier
                    .sizeIn(minWidth = ROW_MIN)
                    .testTag("$testTag.value")
                    .semantics { stateDescription = valueText ?: TrackingCopy.NOT_LOGGED },
            )
            StepperButton("+", "$testTag.increment", onIncrement)
        }
    }
}

@Composable
private fun StepperButton(glyph: String, testTag: String, onClick: () -> Unit) {
    val colors = LocalBloomColors.current
    Text(
        text = glyph,
        style = bloomBody,
        color = colors.plum,
        modifier = Modifier
            .sizeIn(minWidth = ROW_MIN, minHeight = ROW_MIN)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics { role = Role.Button },
    )
}

/**
 * A segmented picker (intensity, sleep quality) with a leading "none" option so the factor stays
 * nullable. [options] are the non-null choices; [selectedIndex] `null` selects the leading none
 * option (draft stays nil). [onSelect] receives the chosen index or `null` for none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedPickerRow(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    val total = options.size + 1 // +1 for the leading none option.
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = selectedIndex == null,
                onClick = { onSelect(null) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = total),
                modifier = Modifier
                    .sizeIn(minHeight = ROW_MIN)
                    .testTag("$testTagPrefix.none")
                    .semantics { contentDescription = TrackingCopy.NOT_LOGGED },
            ) { Text(TrackingCopy.UNSET_READOUT, color = colors.inkMuted) }
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index + 1, count = total),
                    modifier = Modifier
                        .sizeIn(minHeight = ROW_MIN)
                        .testTag("$testTagPrefix.$index"),
                ) { Text(optionLabel(option)) }
            }
        }
    }
}

/**
 * A reveal-on-opt-in row (stress) — collapsed it shows [label] + "Not logged"; tapping opts in and
 * reveals [content] (typically a [SeverityControl]) + a "clear" affordance the caller wires. The
 * draft stays nil until [onOptIn]. [optedIn] gates the reveal.
 */
@Composable
fun RevealOnOptInRow(
    label: String,
    optedIn: Boolean,
    onOptIn: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalBloomColors.current
    if (!optedIn) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .sizeIn(minHeight = ROW_MIN)
                .clickable(onClick = onOptIn)
                .testTag(testTag)
                .semantics { role = Role.Button; contentDescription = "$label, ${TrackingCopy.NOT_LOGGED}" }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = bloomBody, color = colors.ink)
            Text(text = TrackingCopy.NOT_LOGGED, style = bloomBody, color = colors.inkMuted)
        }
    } else {
        content()
    }
}

/**
 * The mood 5-item row (1..5). [selected] `null` = not logged; each item is `Role.RadioButton`,
 * ≥48dp; re-tap clears (the CALLER passes `onSelect(null)` when the tapped value is already selected).
 */
@Composable
fun MoodRow(
    selected: Int?,
    onSelect: (Int?) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..5).forEach { value ->
            val isSel = selected == value
            Text(
                text = TrackingCopy.moodLabels.getValue(value),
                style = bloomBody,
                color = if (isSel) colors.plum else colors.inkMuted,
                modifier = Modifier
                    .sizeIn(minWidth = ROW_MIN, minHeight = ROW_MIN)
                    .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(value) })
                    .testTag("$testTagPrefix.$value")
                    .semantics {
                        contentDescription = TrackingCopy.moodLabels.getValue(value)
                        this.selected = isSel
                    },
            )
        }
    }
}

/**
 * A free-text field (food note, day note, med name/note) — a Bloom-tinted [OutlinedTextField]. Length
 * is bounded at the [EndoRepository][com.endoapp.app.data.EndoRepository] seam, never here.
 */
@Composable
fun FreeTextRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = ROW_MIN)
            .testTag(testTag),
    )
}

/** The leading selection dot for a multi-select row — filled plum when selected, an outline ring else. */
@Composable
private fun SelectionDot(selected: Boolean) {
    val colors = LocalBloomColors.current
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) colors.plum else colors.card)
            .border(1.dp, if (selected) colors.plum else colors.inkMuted, CircleShape),
    )
}
