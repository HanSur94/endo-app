package com.endoapp.app.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * The Material 3 `ColorScheme` DERIVED from the Bloom tokens per `19-UI-SPEC.md`
 * §Material 3 `ColorScheme` mapping — so stock M3 components (`Button`, `NavigationBar`, `Text`,
 * `Surface`, sheets) inherit Bloom without per-call overrides. This is the Compose analog of iOS's
 * `BloomTabBarAppearance`.
 *
 * `surfaceTint = Color.Transparent` DISABLES M3 tonal-elevation tinting — Bloom owns its shadows
 * and fills, so raised surfaces (card, surface) stay flat and on-brand instead of being recolored
 * by an elevation overlay. This is NOT Material You: no wallpaper color is ever sampled.
 */
private val bloomLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF6E2153),              // plum
    onPrimary = Color(0xFFFFFFFF),            // card
    primaryContainer = Color(0xFFF7E3EA),     // petal
    onPrimaryContainer = Color(0xFF6E2153),   // plum
    secondary = Color(0xFFEF6E8E),            // rose
    onSecondary = Color(0xFFFFFFFF),          // card
    secondaryContainer = Color(0xFFF7E3EA),   // petal
    onSecondaryContainer = Color(0xFF4A1338), // plumDeep
    tertiary = Color(0xFFF7B544),             // gold
    onTertiary = Color(0xFF2A1020),           // ink
    background = Color(0xFFE9E0E6),           // canvas
    onBackground = Color(0xFF2A1020),         // ink
    surface = Color(0xFFFBF8FA),             // surface
    onSurface = Color(0xFF2A1020),           // ink
    surfaceVariant = Color(0xFFF3ECF0),       // surfaceSunken
    onSurfaceVariant = Color(0xFF7A5E70),     // inkSecondary
    surfaceContainerLowest = Color(0xFFFFFFFF), // card
    surfaceContainer = Color(0xFFFBF8FA),     // surface
    outline = Color(0xFFE8DCE5),             // strokeSubtle
    outlineVariant = Color(0xFFEDD9E4),       // petalLine
    error = Color(0xFFDC4F73),               // roseDeep
    onError = Color(0xFFFFFFFF),             // card
    errorContainer = Color(0xFFF7E3EA),       // petal
    onErrorContainer = Color(0xFF4A1338),     // plumDeep
    scrim = Color(0xFF4A1338),               // plumDeep
    surfaceTint = Color.Transparent,          // disable tonal-elevation tinting (flat Bloom surfaces)
)

/**
 * The Bloom theme wrapper. Every screen composes inside `BloomTheme { … }`:
 * - provides [LocalBloomColors] (the 17 brand tokens) down the composition, and
 * - wraps content in a `MaterialTheme` carrying the derived [bloomLightColorScheme],
 *   [BloomTypography], and [BloomShapes].
 *
 * Light theme only this phase. NEVER call `dynamicLightColorScheme()`/`dynamicDarkColorScheme()` —
 * Bloom tokens are brand- and chart-semantic; wallpaper recoloring is a locked anti-feature
 * (guarded by `NoDynamicColorScanTest`).
 */
@Composable
fun BloomTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBloomColors provides bloomLightColors) {
        MaterialTheme(
            colorScheme = bloomLightColorScheme,
            typography = BloomTypography,
            shapes = BloomShapes,
            content = content,
        )
    }
}
