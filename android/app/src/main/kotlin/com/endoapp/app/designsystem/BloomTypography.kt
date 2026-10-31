package com.endoapp.app.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The Bloom type scale — a port of the shipped iOS `BloomTypography.swift` per `19-UI-SPEC.md`
 * §Typography. Editorial headings use `FontFamily.Serif` (Noto Serif on Android) + `SemiBold`;
 * body/UI uses `FontFamily.Default` (Roboto). No bundled font assets (mirrors iOS).
 *
 * **Weight-count deviation (documented in the UI-SPEC):** the Bloom port intentionally uses THREE
 * weights — Normal(400) body, SemiBold(600) headings/labels/buttons, Bold(700) eyebrow only.
 * The eyebrow's tracked Bold micro-label is the shipped iOS system's signature; do not collapse it.
 */

// --- The full Bloom text scale (source of truth) ---

/** Serif 30/36 SemiBold — screen titles. */
val bloomDisplay: TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
)

/** Serif 28/34 SemiBold — onboarding welcome title. */
val bloomTitle: TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
)

/** Serif 22/28 SemiBold — section headings ("Ready when you are"). No 1:1 M3 slot. */
val bloomH2: TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
)

/** Serif 19/24 SemiBold — placeholder headings. */
val bloomH3: TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 24.sp,
)

/**
 * Default 12/16 Bold — UPPERCASE eyebrow, `letterSpacing = 1.4.sp` (iOS `kerning 1.4`).
 * Uppercase is applied at the call site (`text.uppercase()`), tint `inkMuted` from LocalBloomColors.
 */
val bloomEyebrow: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.4.sp,
)

/** Default 16/24 Normal — paragraph copy. */
val bloomBody: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
)

/** Default 17/20 SemiBold — primary CTA label (white on plum). */
val bloomButtonLabel: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 20.sp,
)

/** Default 14/18 SemiBold — chips, tab labels. */
val bloomChipLabel: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 18.sp,
)

/**
 * The Bloom `Typography` mapping the M3 template roles onto the Bloom scale per the UI-SPEC:
 * `displaySmall`→bloomDisplay, `titleLarge`→bloomTitle, `titleMedium`→bloomH3,
 * `bodyLarge`→body, `labelLarge`→buttonLabel, `labelSmall`→bloomEyebrow. The extra Bloom
 * styles the M3 slots do not cover (bloomH2, bloomChipLabel) are consumed as public TextStyles.
 */
val BloomTypography: Typography = Typography(
    displaySmall = bloomDisplay,
    titleLarge = bloomTitle,
    titleMedium = bloomH3,
    bodyLarge = bloomBody,
    labelLarge = bloomButtonLabel,
    labelSmall = bloomEyebrow,
)
