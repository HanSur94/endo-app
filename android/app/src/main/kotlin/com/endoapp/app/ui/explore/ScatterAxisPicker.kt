// The scatter axis picker — two QUANTITATIVE-ONLY axis selectors (X / Y). The Android port of
// `ScatterAxisPicker.swift` (22-UI-SPEC §B Scatter mode).
//
// QUANTITATIVE-ONLY (H5 / the scatter honesty contract): a presence/event series has no numeric value, so
// it can NEVER be a scatter axis. The caller passes `ExploreSelection.offered(suppressed).filter {
// it.scaleKind.isQuantitative }`, so bleeding is absent under suppression (the picker offers no bleeding
// axis) AND every presence series is already excluded. Choosing the SAME series on both axes is allowed.
package com.endoapp.app.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ExploreCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomEyebrow
import com.endoapp.insights.ExploreSeries

/**
 * The two scatter axis selectors. [offered] is the quantitative-only, suppression-filtered series list;
 * [x] / [y] are the current axis selections (null = unchosen → the placeholder shows). [onX] / [onY] set
 * one axis. Renders inside the Explore `Column` (no scroll of its own).
 */
@Composable
fun ScatterAxisPicker(
    offered: List<ExploreSeries>,
    x: ExploreSeries?,
    y: ExploreSeries?,
    onX: (ExploreSeries) -> Unit,
    onY: (ExploreSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("explore.scatter.axes"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AxisSelector(
            label = ExploreCopy.SCATTER_AXIS_X_LABEL,
            selected = x,
            offered = offered,
            onSelect = onX,
            fieldTestTag = "explore.scatter.axisX",
        )
        AxisSelector(
            label = ExploreCopy.SCATTER_AXIS_Y_LABEL,
            selected = y,
            offered = offered,
            onSelect = onY,
            fieldTestTag = "explore.scatter.axisY",
        )
    }
}

/**
 * One axis field: an eyebrow label + a tappable field that opens a [DropdownMenu] of the [offered] series.
 * The field carries [fieldTestTag]; each menu item carries `${fieldTestTag}.${series.id}`. The field is
 * ≥48dp (touch floor). Shows the selected series' display name, or the placeholder when unchosen.
 */
@Composable
private fun AxisSelector(
    label: String,
    selected: ExploreSeries?,
    offered: List<ExploreSeries>,
    onSelect: (ExploreSeries) -> Unit,
    fieldTestTag: String,
) {
    val colors = LocalBloomColors.current
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label.uppercase(), style = bloomEyebrow, color = colors.inkSecondary)

        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .background(colors.card, RoundedCornerShape(10.dp))
                    .border(1.dp, colors.strokeSubtle, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag(fieldTestTag)
                    // Merge the field's value Text so the field node carries the selected series name
                    // (TalkBack reads the whole field; tests assert its text).
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(
                    text = selected?.displayName ?: ExploreCopy.SCATTER_AXIS_PLACEHOLDER,
                    style = bloomBody,
                    fontWeight = if (selected != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected != null) colors.ink else colors.inkMuted,
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(260.dp),
            ) {
                offered.forEach { series ->
                    DropdownMenuItem(
                        text = { Text(text = series.displayName, style = bloomBody, color = colors.ink) },
                        onClick = {
                            onSelect(series)
                            expanded = false
                        },
                        modifier = Modifier.testTag("$fieldTestTag.${series.id}"),
                    )
                }
            }
        }
    }
}
