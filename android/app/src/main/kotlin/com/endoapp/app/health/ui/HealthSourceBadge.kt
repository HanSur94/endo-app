// The "from Health Connect" provenance chip (Phase 21, AHC-03) — the Kotlin analog of the iOS
// `HealthSourceBadge.swift`. Rendered next to any imported value so the user always knows a value came
// from Health Connect, never from their own logging. It is a PURE SOURCE TAG — never a claim about the
// value. Any `ImportedSample` row is, by definition, from Health Connect, so the day log attaches this
// chip wherever an imported value surfaces (wired in Plan 05 / Phase 22).
package com.endoapp.app.health.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.endoapp.app.content.HealthConnectCopy
import com.endoapp.app.designsystem.BloomChip

/**
 * A small Bloom petal chip reading [HealthConnectCopy.PROVENANCE_BADGE] ("from Health Connect"). The
 * whole chip is ONE accessibility node reading the same string (text carries the meaning — color is
 * never the sole carrier). Stable testTag `hc.sourceBadge` so the import screen / day log can attach it
 * to imported values and the E2E can assert provenance without copy-string matching.
 */
@Composable
fun HealthSourceBadge(modifier: Modifier = Modifier) {
    BloomChip(
        text = HealthConnectCopy.PROVENANCE_BADGE,
        modifier = modifier
            .testTag("hc.sourceBadge")
            .semantics(mergeDescendants = true) {
                contentDescription = HealthConnectCopy.PROVENANCE_BADGE
            },
    )
}
