package com.endoapp.app.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Bloom component contracts — a Compose port of the iOS Bloom components per
 * `19-UI-SPEC.md` §Bloom component contracts. Every component reads its brand tokens from
 * [LocalBloomColors] so it inherits whatever theme wraps it; component insets (card 18dp,
 * button 52dp, chip h10/v6, radii 22/16/pill) are transcribed verbatim from the shipped iOS system.
 */

/**
 * `bloomCard()` — fill [BloomColors.card] in the 22dp Bloom card shape, a 1dp
 * [BloomColors.strokeSubtle] border, a soft plum-tinted shadow (≈6dp elevation — Bloom owns its
 * shadows, not M3 tonal elevation), interior [padding] (18dp default), full width. Content laid
 * out by the caller is left-aligned by convention (use a `Column(horizontalAlignment = Start)`).
 */
@Composable
fun Modifier.bloomCard(padding: Dp = 18.dp): Modifier {
    val colors = LocalBloomColors.current
    return this
        .fillMaxWidth()
        .shadow(
            elevation = 6.dp,
            shape = BloomCardShape,
            ambientColor = colors.plum,
            spotColor = colors.plum,
        )
        .clip(BloomCardShape)
        .background(colors.card)
        .border(1.dp, colors.strokeSubtle, BloomCardShape)
        .padding(padding)
}

/**
 * `BloomPrimaryButton` — full-width plum capsule CTA. `RoundedCornerShape(50)`, container
 * [BloomColors.plum], white 17sp SemiBold label ([bloomButtonLabel]), min height 52dp (≥48dp
 * target), press opacity ≈0.82 (iOS `BloomPrimaryButtonStyle`).
 */
@Composable
fun BloomPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalBloomColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 52.dp)
            .graphicsLayer { alpha = if (pressed) 0.82f else 1f },
        enabled = enabled,
        shape = BloomPillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.plum,
            contentColor = colors.card,
        ),
        interactionSource = interactionSource,
    ) {
        Text(text = text, style = bloomButtonLabel)
    }
}

/**
 * `BloomChip` — a rounded petal pill. Row of an optional leading [leadingIcon] + a 12sp SemiBold
 * label, [tint] foreground (plum default; pass gold for the later association variant) on
 * [BloomColors.petal], 1dp [BloomColors.petalLine] border, padding h10/v6.
 */
@Composable
fun BloomChip(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    tint: Color = LocalBloomColors.current.plum,
) {
    val colors = LocalBloomColors.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.petal)
            .border(1.dp, colors.petalLine, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text = text, style = bloomChipLabel, color = tint)
    }
}

/**
 * `EyebrowLabel` — the tracked UPPERCASE micro-label. Bold, [bloomEyebrow] style
 * (`letterSpacing = 1.4.sp`), [color] tint ([BloomColors.inkMuted] default). Uppercasing is applied
 * here (`text.uppercase()`) so callers pass natural-case copy.
 */
@Composable
fun EyebrowLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalBloomColors.current.inkMuted,
) {
    Text(text = text.uppercase(), style = bloomEyebrow, color = color, modifier = modifier)
}

/**
 * `DisclaimerBanner` — a petal-filled advisory banner. Leading decorative info glyph tinted
 * [BloomColors.plum] + the passed [text] at 14sp [BloomColors.inkSecondary], 16dp banner shape,
 * 1dp [BloomColors.petalLine] border, 14dp padding, and MERGED semantics so TalkBack reads the
 * whole banner as ONE node. The banner takes its copy as a parameter — it does NOT own the
 * disclaimer constant (that arrives in Plan 04).
 */
@Composable
fun DisclaimerBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(BloomBannerShape)
            .background(colors.petal)
            .border(1.dp, colors.petalLine, BloomBannerShape)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = bloomInfoGlyph,
            contentDescription = null,
            tint = colors.plum,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = bloomBody.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = colors.inkSecondary,
        )
    }
}

/**
 * A minimal bundled "info" outline glyph (Material info_outline path data) — asset-light, mirrors
 * the iOS build-in-code ethos and avoids pulling `material-icons-extended`. Single fill color;
 * the actual tint is applied by the `Icon` composable.
 */
private val bloomInfoGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "BloomInfo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // dot
            moveTo(11f, 7f); horizontalLineToRelative(2f); verticalLineToRelative(2f)
            horizontalLineToRelative(-2f); close()
            // stem
            moveTo(11f, 11f); horizontalLineToRelative(2f); verticalLineToRelative(6f)
            horizontalLineToRelative(-2f); close()
            // outer ring
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
            reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
            reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
            reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
            close()
        }
    }.build()
}
