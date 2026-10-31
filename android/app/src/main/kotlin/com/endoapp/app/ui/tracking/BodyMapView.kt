// EndoApp (:app) — the accessible BodyMap pain picker (SYMP-02, Plan 20-03). A Compose Canvas
// silhouette with one FOCUSABLE ≥48dp hotspot per BodyRegion carrying full TalkBack semantics
// (Role.Button + a neutral non-gendered contentDescription + a "severity N of 10"/"not marked"
// stateDescription + a `bodymap.region.<raw>` testTag), a marked-areas summary, AND a first-class
// non-visual "All areas (list)" fallback that completes the map without the visual. A region tap
// (hotspot OR list row) opens the SAME severity sheet and sets the SAME DayDraft.pain point.
//
// THE NON-VISUAL PATH IS A HARD REQUIREMENT (mirrors iOS SYMP-02): severity is conveyed by NUMBER
// (a centered badge) + tint, NEVER color alone; every region is reachable and operable through the
// list fallback with no dependence on the silhouette.
package com.endoapp.app.ui.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.DayLogCopy
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomChipLabel
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.insights.BodyRegion

/** The visible dot diameter inside the ≥48dp hit target (number + tint carry the meaning). */
private val DOT_SIZE = 26.dp

/** The effective (≥48dp) hotspot hit target. */
private val HOTSPOT_SIZE = 48.dp

/**
 * The accessible BodyMap. [draft] carries the current `pain: Map<BodyRegion, Int?>`; [onSetRegion]
 * marks a region at a severity, [onClearRegion] un-marks it — BOTH the hotspot map and the list
 * fallback route through these two callbacks so the visual and non-visual paths write the SAME point.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMapView(
    draft: DayDraft,
    onSetRegion: (BodyRegion, Int) -> Unit,
    onClearRegion: (BodyRegion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var side by remember { mutableStateOf(BodySide.FRONT) }
    var sheetRegion by remember { mutableStateOf<BodyRegion?>(null) }
    var listExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SideToggle(side = side, onSide = { side = it })
        Silhouette(side = side, draft = draft, onOpen = { sheetRegion = it })
        MarkedSummary(draft = draft)
        ListFallback(
            expanded = listExpanded,
            onToggleExpanded = { listExpanded = !listExpanded },
            draft = draft,
            onOpen = { sheetRegion = it },
        )
    }

    sheetRegion?.let { region ->
        RegionSheet(
            region = region,
            draft = draft,
            onSetRegion = onSetRegion,
            onClearRegion = onClearRegion,
            onDismiss = { sheetRegion = null },
        )
    }
}

/** The front/back segmented toggle (group labelled "Body side", each option Role.RadioButton). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideToggle(side: BodySide, onSide: (BodySide) -> Unit) {
    val colors = LocalBloomColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = DayLogCopy.BODY_SIDE, style = bloomBody, color = colors.inkSecondary)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.testTag("bodymap.sideToggle")) {
            val options = listOf(BodySide.FRONT to DayLogCopy.FRONT, BodySide.BACK to DayLogCopy.BACK)
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = side == value,
                    onClick = { onSide(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier
                        .sizeIn(minHeight = HOTSPOT_SIZE)
                        .testTag("bodymap.side.${value.name.lowercase()}")
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = label
                            selected = side == value
                        },
                ) { Text(label) }
            }
        }
    }
}

/** The Canvas silhouette with the current side's hotspots overlaid as ≥48dp focusable buttons. */
@Composable
private fun Silhouette(side: BodySide, draft: DayDraft, onOpen: (BodyRegion) -> Unit) {
    val colors = LocalBloomColors.current
    val hotspots = BodySilhouette.hotspotsFor(side)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .aspectRatio(BodySilhouette.ASPECT)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceSunken)
            // Neighbouring ≥48dp hotspots overlap (the pelvic cluster sits ~0.12 of the width apart),
            // so a tap goes to the NEAREST hotspot centre rather than whichever box is drawn on top.
            .pointerInput(side) {
                detectTapGestures { tap ->
                    val maxReach = HOTSPOT_SIZE.toPx()
                    hotspots
                        .map { it to (Offset(it.x * size.width, it.y * size.height) - tap).getDistance() }
                        .filter { it.second <= maxReach }
                        .minByOrNull { it.second }
                        ?.let { onOpen(it.first.region) }
                }
            },
    ) {
        val boxW = maxWidth
        val boxH = maxHeight
        // The silhouette itself is decorative — the hotspots carry the meaning.
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(BodySilhouette.ASPECT)) {
            drawPath(path = BodySilhouette.path(size), brush = SolidColor(colors.inkMuted.copy(alpha = 0.25f)))
        }
        hotspots.forEach { hotspot ->
            Hotspot(
                region = hotspot.region,
                severity = draft.pain[hotspot.region],
                marked = draft.pain.containsKey(hotspot.region),
                offsetX = boxW * hotspot.x - HOTSPOT_SIZE / 2,
                offsetY = boxH * hotspot.y - HOTSPOT_SIZE / 2,
                onClick = { onOpen(hotspot.region) },
            )
        }
    }
}

/**
 * One hotspot — a ≥48dp Role.Button carrying the region label + a "severity N of 10"/"not marked"
 * stateDescription + a `bodymap.region.<raw>` testTag. Severity is a centered numeric badge + a rose
 * halo ramped by severity (NUMBER + tint, never color alone).
 */
@Composable
private fun Hotspot(
    region: BodyRegion,
    severity: Int?,
    marked: Boolean,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val colors = LocalBloomColors.current
    val label = TrackingCopy.bodyRegionLabels.getValue(region)
    val state = severity?.let { TrackingCopy.severityStateDescription(it) } ?: TrackingCopy.NOT_MARKED
    // Halo alpha ramps with severity (0..10) so the tint reinforces the number; unmarked = faint ring.
    val haloAlpha = if (marked) 0.20f + 0.06f * ((severity ?: 0).coerceIn(0, 10)) else 0f

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(HOTSPOT_SIZE)
            .testTag("bodymap.region.${region.raw}")
            // Touch is routed by the silhouette's nearest-centre handler; the semantic click keeps the
            // hotspot a real TalkBack/Switch Access button.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                stateDescription = state
                onClick(label = label) { onClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(DOT_SIZE)
                .clip(CircleShape)
                .background(if (marked) colors.plum else colors.card)
                .background(colors.rose.copy(alpha = haloAlpha), CircleShape)
                .border(1.5.dp, colors.rose, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (marked && severity != null) {
                Text(text = "$severity", style = bloomChipLabel, color = colors.card)
            }
        }
    }
}

/** The marked-areas summary — petal pills (region label + N/10); the value always carries meaning. */
@Composable
private fun MarkedSummary(draft: DayDraft) {
    val colors = LocalBloomColors.current
    val marked = BodyRegion.entries.mapNotNull { region ->
        draft.pain[region]?.let { sev -> region to sev }
    }
    if (marked.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().testTag("bodymap.markedSummary"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        marked.forEach { (region, sev) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = TrackingCopy.bodyRegionLabels.getValue(region), style = bloomBody, color = colors.ink)
                Text(text = TrackingCopy.severitySummary(sev), style = bloomBody, color = colors.inkSecondary)
            }
        }
    }
}

/**
 * The non-visual "All areas (list)" fallback — a disclosure listing ALL ten regions as Role.Button
 * rows (same contentDescription + stateDescription as the hotspots) opening the SAME severity sheet.
 * TalkBack users complete the pain map entirely through this list (SYMP-02).
 */
@Composable
private fun ListFallback(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    draft: DayDraft,
    onOpen: (BodyRegion) -> Unit,
) {
    val colors = LocalBloomColors.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = DayLogCopy.ALL_AREAS,
            style = bloomBody,
            color = colors.plum,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = HOTSPOT_SIZE)
                .clickable(onClick = onToggleExpanded)
                .testTag("bodymap.listFallback")
                .semantics { role = Role.Button; contentDescription = DayLogCopy.ALL_AREAS }
                .padding(vertical = 12.dp),
        )
        if (expanded) {
            BodyRegion.entries.forEach { region ->
                val label = TrackingCopy.bodyRegionLabels.getValue(region)
                val sev = draft.pain[region]
                val state = sev?.let { TrackingCopy.severityStateDescription(it) } ?: TrackingCopy.NOT_MARKED
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = HOTSPOT_SIZE)
                        .clickable { onOpen(region) }
                        .testTag("bodymap.list.${region.raw}")
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = label
                            stateDescription = state
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = label, style = bloomBody, color = colors.ink)
                    if (sev != null) {
                        Text(
                            text = TrackingCopy.severitySummary(sev),
                            style = bloomBody,
                            color = colors.inkSecondary,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}

/** The region severity sheet — the SeverityControl + Clear-this-area (disabled unmarked) + Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionSheet(
    region: BodyRegion,
    draft: DayDraft,
    onSetRegion: (BodyRegion, Int) -> Unit,
    onClearRegion: (BodyRegion) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalBloomColors.current
    val marked = draft.pain.containsKey(region)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = DayLogCopy.PAIN_AREA, style = bloomH3, color = colors.ink)
            Text(text = TrackingCopy.bodyRegionLabels.getValue(region), style = bloomBody, color = colors.inkSecondary)
            SeverityControl(
                value = draft.pain[region] ?: 0,
                onValueChange = { onSetRegion(region, it) },
                modifier = Modifier.testTag("bodymap.region.${region.raw}.severity"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { onClearRegion(region); onDismiss() },
                    enabled = marked,
                    modifier = Modifier.testTag("bodymap.region.${region.raw}.clear"),
                ) { Text(DayLogCopy.CLEAR_AREA, color = if (marked) colors.rose else colors.inkMuted) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("bodymap.region.${region.raw}.done"),
                ) { Text(DayLogCopy.DONE, color = colors.plum) }
            }
        }
    }
}
