package com.endoapp.app.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the Bloom theme wires the brand tokens into BOTH the derived M3 `ColorScheme` and the
 * `LocalBloomColors` composition local — so stock M3 components render on-brand and screens can
 * read the full 17-token palette. Runs on the JVM Robolectric+Compose lane (BloomHarnessSmokeTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BloomThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeExposesPlumPrimaryAndBloomTokens() {
        var schemePrimary: Color? = null
        var bloomPlum: Color? = null
        var bloomCanvas: Color? = null

        composeRule.setContent {
            BloomTheme {
                schemePrimary = MaterialTheme.colorScheme.primary
                bloomPlum = LocalBloomColors.current.plum
                bloomCanvas = LocalBloomColors.current.canvas
            }
        }

        composeRule.runOnIdle {
            // The derived M3 scheme's primary is Bloom plum — stock Buttons etc. render on-brand.
            assertEquals(Color(0xFF6E2153), schemePrimary)
            // The full Bloom palette flows through LocalBloomColors.
            assertEquals(Color(0xFF6E2153), bloomPlum)
            assertEquals(Color(0xFFE9E0E6), bloomCanvas)
        }
    }

    @Test
    fun allSeventeenTokensCarryTheUiSpecHexValues() {
        val c = bloomLightColors
        assertEquals(Color(0xFFE9E0E6), c.canvas)
        assertEquals(Color(0xFFFBF8FA), c.surface)
        assertEquals(Color(0xFFF3ECF0), c.surfaceSunken)
        assertEquals(Color(0xFFFFFFFF), c.card)
        assertEquals(Color(0xFF2A1020), c.ink)
        assertEquals(Color(0xFF7A5E70), c.inkSecondary)
        assertEquals(Color(0xFFA997A4), c.inkMuted)
        assertEquals(Color(0xFFEF6E8E), c.rose)
        assertEquals(Color(0xFFDC4F73), c.roseDeep)
        assertEquals(Color(0xFF6E2153), c.plum)
        assertEquals(Color(0xFF4A1338), c.plumDeep)
        assertEquals(Color(0xFFF7B544), c.gold)
        assertEquals(Color(0xFFF7E3EA), c.petal)
        assertEquals(Color(0xFFEDD9E4), c.petalLine)
        assertEquals(Color(0xFFE8DCE5), c.strokeSubtle)
        assertEquals(Color(0xFF3DA5A0), c.teal)
        assertEquals(Color(0xFF5B6CB0), c.indigo)
    }
}
