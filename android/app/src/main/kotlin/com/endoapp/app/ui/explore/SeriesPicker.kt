// The Explore series picker — a toggle-chip catalog over the suppression-filtered offered series plus a
// range segmented control. The Android port of `SeriesPickerView.swift`.
//
// THE CAP RULE (22-UI-SPEC §B): at most FOUR QUANTITATIVE series at once. At the cap, every UNSELECTED
// quantitative chip disables and the [ExploreCopy.CAP_HINT] shows; event/presence chips are UNCAPPED
// (they stack as separate event lanes, not quantitative overlay lanes). Selected vs unselected chips
// differ by FILL + ICON + WEIGHT — NEVER hue alone (colorblind-safe, 22-UI-SPEC §Accessibility).
//
// The cap arithmetic is a PURE function ([quantitativeSelectionAtCap]) so it is unit-provable without
// rendering; the chip only consults it.
package com.endoapp.app.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ExploreCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomChipLabel
import com.endoapp.app.designsystem.bloomEyebrow
import com.endoapp.insights.ExploreSeries

/** The maximum QUANTITATIVE series that may overlay at once (each its own real-unit lane). Event series
 *  are uncapped. Mirrors the iOS `SeriesPicker` cap. */
const val MAX_QUANTITATIVE_SERIES: Int = 4

/** The descriptive time windows offered by the range control. `days` sets the x-domain span (asOf-back).
 *  Descriptive-only — NOT the engine's `TrendWindow` (Explore bypasses the engine). */
enum class ExploreRange(val days: Long, val key: String, val label: String) {
    DAYS_30(30, "30d", ExploreCopy.RANGE_30),
    DAYS_90(90, "90d", ExploreCopy.RANGE_90),
    DAYS_180(180, "180d", ExploreCopy.RANGE_180),
}

/** The default range on first appear (widest-but-focused). */
val DEFAULT_EXPLORE_RANGE: ExploreRange = ExploreRange.DAYS_90

/**
 * PURE cap predicate: are 4 (== [MAX_QUANTITATIVE_SERIES]) quantitative series already selected? At the
 * cap, an UNSELECTED quantitative chip is disabled. Event series never count toward the cap. Unit-provable.
 */
fun quantitativeSelectionAtCap(selected: Set<ExploreSeries>): Boolean =
    selected.count { it.scaleKind.isQuantitative } >= MAX_QUANTITATIVE_SERIES

/**
 * PURE chip-enabled predicate: a chip is tappable unless it is an UNSELECTED quantitative chip while the
 * quantitative cap is reached. A selected chip is ALWAYS enabled (so it can be removed); an event chip is
 * ALWAYS enabled (uncapped).
 */
fun chipEnabled(series: ExploreSeries, selected: Set<ExploreSeries>): Boolean {
    if (series in selected) return true
    if (!series.scaleKind.isQuantitative) return true
    return !quantitativeSelectionAtCap(selected)
}

/**
 * The suppression-filtered series picker. [offered] is `ExploreSelection.offered(suppressed)` (bleeding
 * already removed for a suppressed user); [selected] is the ephemeral chip set; [onToggle] flips one
 * chip. The range control drives the x-domain window. Renders inside the Explore `Column` (no scroll of
 * its own).
 */
@Composable
fun SeriesPicker(
    offered: List<ExploreSeries>,
    selected: Set<ExploreSeries>,
    onToggle: (ExploreSeries) -> Unit,
    range: ExploreRange,
    onRange: (ExploreRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    val atCap = quantitativeSelectionAtCap(selected)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("explore.picker"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // One horizontally scrolling row (iOS quick-260618-eei parity): ~40 wrapped chips pushed the lanes
        // below the fold. Selected chips lead so the current choice stays visible without scrolling.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            offered.sortedByDescending { it in selected }.forEach { series ->
                val isSelected = series in selected
                val enabled = chipEnabled(series, selected)
                SeriesChip(
                    label = series.displayName,
                    isSelected = isSelected,
                    enabled = enabled,
                    testTag = "explore.chip.${series.id}",
                    onClick = { onToggle(series) },
                )
            }
        }

        // The cap hint appears ONLY at the quantitative cap — the honest "why can't I add more" note.
        if (atCap) {
            Text(
                text = ExploreCopy.CAP_HINT,
                style = bloomChipLabel,
                fontWeight = FontWeight.Normal,
                color = colors.inkSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explore.picker.capHint"),
            )
        }

        // The range segmented control (x-domain window).
        ExploreSegmentedToggle(
            options = ExploreRange.entries,
            selectedOption = range,
            onSelect = onRange,
            label = { it.label },
            optionTestTag = { "explore.range.${it.key}" },
            containerTestTag = "explore.range",
        )
    }
}

/**
 * A single petal/plum toggle chip. Selected vs unselected differ by FILL (plum vs petal) + ICON (a filled
 * dot vs an outline ring) + WEIGHT (SemiBold vs Normal) — NEVER hue alone. A disabled chip dims and drops
 * its click + carries the `disabled` semantics (so `assertIsNotEnabled` sees it). The chip carries a
 * `selected` semantics flag + an add/remove a11y action label.
 */
@Composable
private fun SeriesChip(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = LocalBloomColors.current
    val fill = if (isSelected) colors.plum else colors.petal
    val fg = if (isSelected) Color.White else colors.ink
    val a11yAction = if (isSelected) ExploreCopy.CHIP_REMOVE else ExploreCopy.CHIP_ADD

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, colors.petalLine, CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag)
            .semantics {
                selected = isSelected
                contentDescription = a11yAction
                if (!enabled) disabled()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Redundant-with-fill glyph: a filled dot when selected, an outline ring when not (icon cue, not
        // hue). Drawn as a tiny bordered circle so no icon asset is needed.
        Row(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .then(
                    if (isSelected) Modifier.background(Color.White)
                    else Modifier.border(1.5.dp, colors.plum, CircleShape),
                ),
        ) {}
        Text(
            text = label,
            style = bloomChipLabel,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
        )
    }
}

/**
 * A small custom segmented control (the mode toggle + the range control both use it). A Row of equal
 * selectable segments; the active segment fills `plum` with white SemiBold text, inactive segments sit on
 * `petal`. Custom (not the experimental M3 `SegmentedButton`) to avoid experimental-API coupling while
 * keeping full Bloom visual + testTag control. Each segment carries a `selected` semantics flag.
 */
@Composable
fun <T> ExploreSegmentedToggle(
    options: List<T>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    optionTestTag: (T) -> String,
    containerTestTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.petal)
            .border(1.dp, colors.petalLine, CircleShape)
            .padding(2.dp)
            .testTag(containerTestTag),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selectedOption
            Text(
                text = label(option).uppercase(),
                style = bloomEyebrow,
                color = if (active) Color.White else colors.inkSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) colors.plum else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag(optionTestTag(option))
                    .semantics { selected = active },
            )
        }
    }
}
