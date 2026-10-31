// The kind-agnostic Insight card — the Android port of `InsightCardView.swift` (22-UI-SPEC §A card
// anatomy). ONE composable renders EVERY kind (flare, trend, cycle-phase, correlation, recommendation):
// there is no per-kind card, and — critically — no per-kind ALARM styling. The flare card uses the
// IDENTICAL neutral `card` fill + `ink` text as a trend card (the calm-flare invariant; pinned by
// `FlareNoAlarmVisualTest`).
//
// Anatomy (top → bottom, inside `Modifier.bloomCard()`):
//   1. headline — `insight.headline` VERBATIM (engine-audited in `:insights`; NEVER re-written here).
//   2. confidence pill — plain-language text + shape icon (omitted on the sparse insufficiency card).
//   3. detail — `insight.detail` VERBATIM.
//   4. supporting — `.trend` embeds the honest `TrendChart`; every other kind shows a WORDS-ONLY
//      evidence line ("Seen across {n} logged days." + window label) — NEVER a coefficient/%/p-value.
//   5. basis — "Based on your logs."
//
// DISMISS (non-destructive, §Interaction): a gray/secondary affordance (NEVER `roseDeep`/red) reachable
// BOTH visually (`…dismiss`) AND via a TalkBack custom action "This isn't right" on the card, routing to
// the screen-supplied `onDismiss(signalKey)` → `coordinator.dismiss` (append-only DismissedInsight; it
// NEVER deletes logged data). The card imports NO notification/alarm API (InsightsSeparationTest).
package com.endoapp.app.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.endoapp.app.PendingScatter
import com.endoapp.app.content.ExploreCopy
import com.endoapp.app.content.InsightsCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomChipLabel
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.app.ui.charts.TrendChart
import com.endoapp.app.ui.charts.TrendChartInput
import com.endoapp.insights.Insight
import com.endoapp.insights.InsightKind

/**
 * Render one [insight] as a Bloom card. [onDismiss] routes the card's `signalKey` to the coordinator's
 * non-destructive dismiss. Kind-agnostic: the caller passes any kind and the same anatomy renders.
 */
@Composable
fun InsightCard(
    insight: Insight,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
    suppressed: Boolean = true,
    onOpenScatter: (PendingScatter) -> Unit = {},
) {
    val colors = LocalBloomColors.current
    val root = "insights.card.${insight.signalKey}"

    // The honest SPARSE card: a per-signal "not enough data yet" notice — the SAME composable, but with
    // NO confidence pill and NO chart (22-UI-SPEC §A states table). Tagged `…insufficient`.
    if (insight.isInsufficiencyNotice) {
        Column(
            modifier = modifier
                .bloomCard()
                .testTag("$root.insufficient"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = insight.headline, style = bloomH3, color = colors.ink)
            Text(text = insight.detail, style = bloomBody, color = colors.inkSecondary)
        }
        return
    }

    val dismissLabel = InsightsCopy.DISMISS_ACTION
    Column(
        modifier = modifier
            .bloomCard()
            .testTag(root)
            // The TalkBack custom action makes dismiss reachable WITHOUT the visual gesture (§A11y).
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label = dismissLabel) {
                        onDismiss(insight.signalKey); true
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Headline (VERBATIM) + the gray/secondary dismiss affordance (NEVER red).
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = insight.headline,
                style = bloomH3,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("$root.dismiss")
                    .clickable { onDismiss(insight.signalKey) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dismissLabel,
                    style = bloomChipLabel,
                    // Gray/secondary — the non-destructive posture. NEVER `roseDeep`/red.
                    color = colors.inkMuted,
                )
            }
        }

        // 2. Confidence pill (plain-language text + shape — never a number, never color alone).
        ConfidencePill(confidence = insight.confidence, testTagRoot = root)

        // 3. Detail (VERBATIM).
        Text(text = insight.detail, style = bloomBody, color = colors.ink)

        // 4. Supporting: the honest trend chart, else a words-only evidence line.
        val window = InsightsCopy.windowLabel(insight.window).replaceFirstChar { it.uppercase() }
        val evidence = "${InsightsCopy.seenAcross(insight.sampleSize)} $window."
        // A single point (the stress/mood window mean) draws a lone dot with no trend to read, so a
        // chart needs at least two dated points; otherwise the words-only evidence line carries it.
        if (insight.kind == InsightKind.TREND && insight.points.size >= 2) {
            TrendChart(
                input = TrendChartInput(
                    points = insight.points,
                    windowStart = insight.points.minOf { it.date },
                    asOf = insight.points.maxOf { it.date },
                    testTagRoot = root,
                ),
                summary = evidence,
            )
        } else {
            Text(
                text = evidence,
                style = bloomBody,
                color = colors.inkSecondary,
                modifier = Modifier.testTag("$root.evidence"),
            )
        }

        // 4b. Scatter deep-link affordance — the `.correlation` arm ONLY. Resolve the STRUCTURAL signalKey
        // ONCE (never the headline/detail prose — the honesty contract, T-22-01j); when it yields two typed
        // quantitative axes, render "See the days behind this". When null the affordance is HIDDEN (not
        // disabled) — a presence-only / sleep-quality / suppressed-bleeding correlation shows no link. The
        // TYPED axes ride the router payload; they are NEVER re-derived from copy downstream.
        if (insight.kind == InsightKind.CORRELATION) {
            val axes = remember(insight.signalKey, suppressed) {
                ScatterDeepLink.resolve(insight.signalKey, suppressed)
            }
            if (axes != null) {
                Box(
                    modifier = Modifier
                        .sizeIn(minHeight = 48.dp)
                        .testTag("$root.scatterLink")
                        .clickable { onOpenScatter(PendingScatter(x = axes.first, y = axes.second)) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = ExploreCopy.SCATTER_LINK,
                        style = bloomChipLabel,
                        fontWeight = FontWeight.SemiBold,
                        // Accent-tinted plum — the reserved deep-link affordance hue.
                        color = colors.plum,
                    )
                }
            }
        }

        // 5. Basis note.
        Text(
            text = InsightsCopy.BASIS_NOTE,
            style = bloomBody,
            color = colors.inkSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
