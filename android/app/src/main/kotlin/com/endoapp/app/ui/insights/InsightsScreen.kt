// The gated Insights dashboard — the Android port of `InsightsView.swift`, filling the Phase-19
// Insights tab placeholder (AINS-01). It reads [InsightsCoordinator.state] and renders the fixed-order
// kind sections (Flare FIRST by POSITION only), the plain-language confidence cards, the three honest
// states, the pinned not-medical-advice disclaimer, and non-destructive dismiss.
//
// INSTANT LOAD + NON-BLOCKING RECOMPUTE (§Interaction): on appear the wrapper calls
// `coordinator.loadCached()` (paints the cache instantly) then `coordinator.dataChanged()` (a debounced
// off-main recompute). While `isUpdating`, ONLY the subtle "Updating…" caption shows — cached content
// stays; there is NEVER a full-screen blocking spinner.
//
// SECTION OMISSION (the honesty mechanism): a kind section with ZERO gated insights is OMITTED ENTIRELY
// — never an empty shell. This is why a suppressed / no-cycle user has the Cycle-phase section provably
// ABSENT (the engine emits zero `.cyclePhase`; the section is omitted). The pure [insightSections] models
// this so the order + omission are testable deterministically on the JVM.
//
// SEPARATION (CONTEXT §5): this package imports NO notification / alarm / WorkManager API — the reminders
// package stays the SOLE notification owner (InsightsSeparationTest keeps this honest).
package com.endoapp.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.endoapp.app.PendingScatter
import com.endoapp.app.content.Disclaimer
import com.endoapp.app.content.InsightsCopy
import com.endoapp.app.designsystem.DisclaimerBanner
import com.endoapp.app.designsystem.EyebrowLabel
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH2
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.app.designsystem.bloomTitle
import com.endoapp.app.insights.InsightsCoordinator
import com.endoapp.app.insights.InsightsUiState
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightKind

/**
 * A fixed-order kind section of the dashboard — its header + the gated insights of that kind. Built by
 * the pure [insightSections] so the block order (Flare FIRST) and omit-when-empty are testable off-device.
 */
data class InsightSection(
    val kind: InsightKind,
    val header: String,
    val testTag: String,
    val insights: List<Insight>,
)

/**
 * The FIXED dashboard block order (22-UI-SPEC §A): Flare FIRST (by POSITION only), then Trends,
 * Cycle-phase, Possible-associations, Recommendations. A kind with zero gated insights is OMITTED — the
 * suppressed-cycle honesty mechanism. Pure + deterministic: order = this list, presence = non-empty.
 */
fun insightSections(insights: List<Insight>): List<InsightSection> {
    val order = listOf(
        Triple(InsightKind.FLARE, InsightsCopy.SECTION_FLARE, "insights.section.flare"),
        Triple(InsightKind.TREND, InsightsCopy.SECTION_TRENDS, "insights.section.trends"),
        Triple(InsightKind.CYCLE_PHASE, InsightsCopy.SECTION_CYCLE_PHASE, "insights.section.cyclePhase"),
        Triple(InsightKind.CORRELATION, InsightsCopy.SECTION_ASSOCIATIONS, "insights.section.correlations"),
        Triple(InsightKind.RECOMMENDATION, InsightsCopy.SECTION_RECOMMENDATIONS, "insights.section.recommendations"),
    )
    return order.mapNotNull { (kind, header, tag) ->
        val ofKind = insights.filter { it.kind == kind }
        if (ofKind.isEmpty()) null else InsightSection(kind, header, tag, ofKind)
    }
}

/**
 * The Insights tab entry point — binds the [coordinator] to the dashboard. On appear it paints the
 * cache instantly then kicks a non-blocking recompute (D-04 / D-01). The coordinator is referenced ONLY
 * here inside the consented shell subtree (consent-by-construction preserved).
 */
@Composable
fun InsightsScreen(
    coordinator: InsightsCoordinator,
    modifier: Modifier = Modifier,
    loadSuppressed: suspend () -> Boolean = { true },
    onOpenScatter: (PendingScatter) -> Unit = {},
) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    // Fail-closed: default suppressed=true until the load resolves, so a bleeding scatter-link can never
    // flash before we know the treatment mode (the ExploreScreen fail-closed posture). The real gate is
    // upstream anyway — the engine emits NO bleeding correlation under suppression.
    var suppressed by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        coordinator.loadCached()
        coordinator.dataChanged()
        suppressed = loadSuppressed()
    }
    InsightsDashboard(
        state = state,
        onDismiss = coordinator::dismiss,
        modifier = modifier,
        suppressed = suppressed,
        onOpenScatter = onOpenScatter,
    )
}

/**
 * The stateless dashboard renderer — driven by a plain [InsightsUiState] + an [onDismiss] closure so it
 * is fully unit-testable without a coordinator, store, or clock. Renders the three honest states:
 * Empty (one warm full-screen state), else the fixed-order sections (each omit-when-empty), with the
 * pinned disclaimer and the non-blocking "Updating…" caption.
 */
@Composable
fun InsightsDashboard(
    state: InsightsUiState,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
    suppressed: Boolean = true,
    onOpenScatter: (PendingScatter) -> Unit = {},
) {
    val colors = LocalBloomColors.current
    val insights = state.insights
    val sections = insightSections(insights)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .testTag("insights.screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Block 1 — serif title + subtitle.
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("insights.header"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Insights", style = bloomTitle, color = colors.ink)
                Text(text = InsightsCopy.SUBTITLE, style = bloomBody, color = colors.inkSecondary)
            }
        }

        // Block 2 — the at-a-glance stat strip (derived counts, shown only when non-empty).
        if (insights.isNotEmpty()) {
            item(key = "statStrip") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("insights.statStrip"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // "Not enough data yet" notices are honesty placeholders, not patterns (iOS parity).
                    val surfaced = insights.filterNot { it.isInsufficiencyNotice }
                    StatTile(value = surfaced.size, label = InsightsCopy.STAT_TOTAL_LABEL, modifier = Modifier.weight(1f))
                    StatTile(
                        value = surfaced.map { it.kind }.distinct().size,
                        label = InsightsCopy.STAT_KINDS_LABEL,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Block 3 — the pinned not-medical-advice disclaimer (reused; renders Disclaimer.text verbatim).
        item(key = "disclaimer") {
            DisclaimerBanner(text = Disclaimer.text, modifier = Modifier.testTag("insights.disclaimer"))
        }

        // Block 4 — the subtle, non-blocking "Updating…" caption (cached content stays visible).
        if (state.isUpdating) {
            item(key = "updating") {
                Row(
                    modifier = Modifier.testTag("insights.updating"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.plum)
                    Text(text = InsightsCopy.UPDATING, style = bloomBody, color = colors.inkSecondary)
                }
            }
        }

        // Empty state — ONE warm full-screen state, NOT empty sections.
        if (insights.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .testTag("insights.empty"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = InsightsCopy.EMPTY_HEADING,
                        style = bloomH3,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = InsightsCopy.EMPTY_BODY,
                        style = bloomBody,
                        color = colors.inkSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // Blocks 5–9 — the fixed-order kind sections (Flare FIRST), each omit-when-empty.
            sections.forEach { section -> kindSection(section, onDismiss, suppressed, onOpenScatter) }
        }

        item(key = "footer") { Spacer(Modifier.size(8.dp)) }
    }
}

/** One kind section: a header row then its cards. Omitted entirely upstream when the kind is empty. */
private fun LazyListScope.kindSection(
    section: InsightSection,
    onDismiss: (String) -> Unit,
    suppressed: Boolean,
    onOpenScatter: (PendingScatter) -> Unit,
) {
    item(key = section.testTag) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(section.testTag),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EyebrowLabel(text = section.header)
            Text(text = section.header, style = bloomH2, color = LocalBloomColors.current.ink)
        }
    }
    items(section.insights, key = { it.id.toString() }) { insight ->
        InsightCard(
            insight = insight,
            onDismiss = onDismiss,
            suppressed = suppressed,
            onOpenScatter = onOpenScatter,
        )
    }
}

/** A single derived-count stat tile (serif numeral + a caption label — no fabricated stats). */
@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
    val colors = LocalBloomColors.current
    Column(
        modifier = modifier.bloomCard(padding = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = value.toString(), style = bloomTitle, color = colors.plum)
        Text(text = label, style = bloomBody, color = colors.inkSecondary)
    }
}
