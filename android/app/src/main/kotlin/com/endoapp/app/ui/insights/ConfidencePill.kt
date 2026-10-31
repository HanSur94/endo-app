// The plain-language confidence pill — the Android port of the iOS confidence chip (22-UI-SPEC
// §Confidence Pill, invariant H6). The load-bearing honesty rule:
//
//   Confidence is TEXT + a SHAPE icon — NEVER a number, NEVER color alone.
//
// The icon conveys the level by SHAPE (filled fraction), so a grayscale / high-contrast user
// distinguishes the three by text + shape without relying on hue. The word comes from the audited
// [InsightsCopy.confidenceWord]; nothing here renders a digit, "%", "p =", or "r =" (pinned by
// `ConfidencePillTest`). Rendered as the Bloom pill contract: `plum` foreground on `petal`, a pill
// (`CircleShape`) with a 1dp `petalLine` border, `h10/v6` padding.
package com.endoapp.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.endoapp.app.content.InsightsCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomChipLabel
import com.endoapp.insights.InsightConfidence

/**
 * The confidence pill for an insight card. [testTagRoot] is the card's `insights.card.<signalKey>` root;
 * the pill tags itself `…confidence`, the shape icon `…confidence.icon`, and the ⓘ affordance
 * `…confidence.info` (opening an in-app qualitative explainer inline). Purely presentational + a local
 * expand toggle — it holds no health state and reads only the level.
 */
@Composable
fun ConfidencePill(
    confidence: InsightConfidence,
    testTagRoot: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    var explainerOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The pill: shape icon + plain-language word (text + shape — the two redundant, non-hue cues).
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.petal)
                    .border(1.dp, colors.petalLine, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("$testTagRoot.confidence"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ConfidenceShapeIcon(
                    confidence = confidence,
                    modifier = Modifier
                        .size(14.dp)
                        .testTag("$testTagRoot.confidence.icon"),
                )
                Text(
                    text = InsightsCopy.confidenceWord(confidence),
                    style = bloomChipLabel,
                    color = colors.plum,
                )
            }

            // The ⓘ affordance — a ≥48dp target that toggles the qualitative explainer inline.
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("$testTagRoot.confidence.info")
                    .clickable { explainerOpen = !explainerOpen }
                    .semantics { contentDescription = InsightsCopy.CONFIDENCE_INFO_TITLE },
                contentAlignment = Alignment.Center,
            ) {
                InfoOutlineIcon(modifier = Modifier.size(18.dp))
            }
        }

        if (explainerOpen) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.strokeSubtle, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .testTag("$testTagRoot.confidence.info.popover"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = InsightsCopy.CONFIDENCE_INFO_TITLE, style = bloomChipLabel, color = colors.ink)
                Text(text = InsightsCopy.CONFIDENCE_INFO_BODY, style = bloomBody, color = colors.inkSecondary)
            }
        }
    }
}

/**
 * The level-by-SHAPE icon (filled fraction): high = a FULLY filled circle, medium = a HALF-filled
 * circle, low/none = an OUTLINE circle. Drawn with the [colors.plum] ink — the shape (not the hue) is
 * the level cue, so it survives grayscale.
 */
@Composable
private fun ConfidenceShapeIcon(confidence: InsightConfidence, modifier: Modifier = Modifier) {
    val plum = LocalBloomColors.current.plum
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension / 2f - stroke / 2f
        // Always draw the outline ring — the shared frame every level carries.
        drawCircle(color = plum, radius = radius, style = Stroke(width = stroke))
        when (confidence) {
            InsightConfidence.HIGH -> drawCircle(color = plum, radius = radius)
            InsightConfidence.MEDIUM ->
                // A filled left half → the "half-filled" cue.
                drawArc(
                    color = plum,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                )
            InsightConfidence.LOW, InsightConfidence.NONE -> Unit // outline only
        }
    }
}

/** A minimal outline "ⓘ" affordance drawn asset-light (no material-icons-extended). */
@Composable
private fun InfoOutlineIcon(modifier: Modifier = Modifier) {
    val ink = LocalBloomColors.current.inkSecondary
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension / 2f - stroke / 2f
        drawCircle(color = ink, radius = radius, style = Stroke(width = stroke))
        // The "i" stem.
        drawLine(
            color = ink,
            start = Offset(center.x, center.y - radius * 0.25f),
            end = Offset(center.x, center.y + radius * 0.5f),
            strokeWidth = stroke,
        )
        // The "i" dot.
        drawCircle(color = ink, radius = stroke, center = Offset(center.x, center.y - radius * 0.55f))
    }
}
