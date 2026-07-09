package com.endoapp.app.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * `BloomNavigationBar` — a Material 3 `NavigationBar` wearing the Bloom floating-capsule chrome per
 * `19-UI-SPEC.md` §Consented shell (`BloomTabBar.swift` ported). It stays a stock `NavigationBar`
 * (not a fully-custom capsule) because that path gives correct edge-to-edge inset handling,
 * predictive-back friendliness, and built-in ≥48dp targets + `Tab` semantics for free.
 *
 * Bloom styling:
 *  - `containerColor = surface` (the lightest Bloom surface), a soft plum-tinted top shadow so the
 *    bar reads as a raised floating chrome (Bloom owns its shadows — M3 tonal elevation is disabled).
 *  - selected icon/text = `plum`, unselected = `inkMuted`, active indicator pill = `petal`.
 *  - each item carries its `testTag`, a 24dp glyph (`contentDescription = null` — the label names
 *    the destination, so TalkBack reads "Log, Tab, 1 of 3, Selected" without duplication).
 *
 * Generic over the tab type `T` so the design-system layer stays foundational — it never imports the
 * `ui.shell` `ConsentedTab`; the shell supplies the `testTag`/`label`/`icon` projections. The caller
 * passes the ordered [tabs], the [selected] tab, and an [onSelect] callback.
 */
@Composable
fun <T> BloomNavigationBar(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    testTag: (T) -> String,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            // Soft plum-tinted top shadow (~12% plum) so the bar floats above the canvas.
            .shadow(
                elevation = 8.dp,
                ambientColor = colors.plum.copy(alpha = 0.12f),
                spotColor = colors.plum.copy(alpha = 0.12f),
            ),
        containerColor = colors.surface,
    ) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = icon(tab),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(text = label(tab), style = bloomChipLabel) },
                modifier = Modifier.testTag(testTag(tab)),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.plum,
                    selectedTextColor = colors.plum,
                    unselectedIconColor = colors.inkMuted,
                    unselectedTextColor = colors.inkMuted,
                    indicatorColor = colors.petal,
                ),
            )
        }
    }
}
