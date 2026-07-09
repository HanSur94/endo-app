package com.endoapp.app.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The 17 canonical Bloom brand tokens — the single source of truth for color in the app.
 *
 * Transcribed byte-for-byte from the shipped iOS `BloomColors.swift` (8-bit sRGB triple →
 * `0xFFRRGGBB`, opacity 1.0) per `19-UI-SPEC.md` §Design Tokens. The Material 3 `ColorScheme`
 * (see [BloomTheme]) is *derived from* these tokens so stock M3 components render on-brand;
 * tokens with no clean 1:1 M3 slot (inkMuted, gold, teal, indigo, plumDeep, roseDeep, petalLine,
 * surfaceSunken, card) are read directly from [LocalBloomColors] at call sites.
 */
data class BloomColors(
    val canvas: Color,        // #E9E0E6 warm app background canvas (60% dominant)
    val surface: Color,       // #FBF8FA lightest surface (sheets, raised areas, nav bar)
    val surfaceSunken: Color, // #F3ECF0 recessed surface (wells, segmented tracks)
    val card: Color,          // #FFFFFF card fill
    val ink: Color,           // #2A1020 primary text (deep plum-black)
    val inkSecondary: Color,  // #7A5E70 secondary text
    val inkMuted: Color,      // #A997A4 muted/tertiary text (eyebrows, captions, inactive tab)
    val rose: Color,          // #EF6E8E rose (hero icon, gradient start, accents)
    val roseDeep: Color,      // #DC4F73 deeper rose (destructive / error slot)
    val plum: Color,          // #6E2153 plum (primary CTA, brand, selected state)
    val plumDeep: Color,      // #4A1338 deeper plum (gradient end, pressed, scrim)
    val gold: Color,          // #F7B544 gold accent (association chips, later phases)
    val petal: Color,         // #F7E3EA petal (active-pill tint, chip bg, banner bg)
    val petalLine: Color,     // #EDD9E4 petal line/border
    val strokeSubtle: Color,  // #E8DCE5 1dp card/divider stroke
    val teal: Color,          // #3DA5A0 teal supporting accent (charts, later phases)
    val indigo: Color,        // #5B6CB0 indigo supporting accent (charts, later phases)
)

/** The Bloom light palette — the only theme this phase (dark theme is later polish). */
val bloomLightColors: BloomColors = BloomColors(
    canvas = Color(0xFFE9E0E6),
    surface = Color(0xFFFBF8FA),
    surfaceSunken = Color(0xFFF3ECF0),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF2A1020),
    inkSecondary = Color(0xFF7A5E70),
    inkMuted = Color(0xFFA997A4),
    rose = Color(0xFFEF6E8E),
    roseDeep = Color(0xFFDC4F73),
    plum = Color(0xFF6E2153),
    plumDeep = Color(0xFF4A1338),
    gold = Color(0xFFF7B544),
    petal = Color(0xFFF7E3EA),
    petalLine = Color(0xFFEDD9E4),
    strokeSubtle = Color(0xFFE8DCE5),
    teal = Color(0xFF3DA5A0),
    indigo = Color(0xFF5B6CB0),
)

/**
 * Brand tokens flow down the composition from [BloomTheme]. `staticCompositionLocalOf` (not
 * `compositionLocalOf`) is correct because the palette is effectively constant for a given theme —
 * reads should not incur recomposition-tracking overhead.
 */
val LocalBloomColors = staticCompositionLocalOf { bloomLightColors }
