// EndoApp (:app) — the real Log tab home (ATRK-01), replacing the Phase-19 `tab.log` placeholder.
// A prominent Daily-check-in CTA, a settings gear, a recent-days list, and an honest empty state.
// Every string comes from the registered [TrackingCopy] / [ShellCopy]; all targets ≥48dp; the recent
// days arrive as a Flow off the consent-gated repository (the host never touches a DAO directly).
package com.endoapp.app.ui.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ShellCopy
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.designsystem.BloomPrimaryButton
import com.endoapp.app.designsystem.EyebrowLabel
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomDisplay
import com.endoapp.app.designsystem.bloomH3
import java.time.LocalDate

/**
 * The Log-home content. [recentDays] is the live day history (newest first) off the repository;
 * [onCheckIn], [onEditDay], and [onOpenSettings] push the corresponding [TrackingRoute] on the
 * in-Log stack.
 */
@Composable
fun LogHomeScreen(
    recentDays: List<DayEntry>,
    onCheckIn: () -> Unit,
    onEditDay: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBloomColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header row: eyebrow + greeting on the left, a settings gear on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EyebrowLabel(ShellCopy.TAB_LOG) // "Log" — reused (ShellCopy owns it; not re-declared).
                Text(text = TrackingCopy.GREETING, style = bloomDisplay, color = colors.ink)
            }
            Text(
                text = "⚙",
                style = bloomDisplay,
                color = colors.inkSecondary,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onOpenSettings)
                    .testTag("log.settingsButton")
                    .semantics { role = Role.Button; contentDescription = "Settings" },
            )
        }

        // The prominent primary CTA.
        BloomPrimaryButton(
            text = TrackingCopy.CHECK_IN_CTA,
            onClick = onCheckIn,
            modifier = Modifier.testTag("log.checkInCta"),
        )

        if (recentDays.isEmpty()) {
            EmptyState()
        } else {
            Text(text = TrackingCopy.LOG_PAST_DAY, style = bloomBody, color = colors.inkSecondary)
            recentDays.forEach { entry -> RecentDayRow(entry, onEditDay) }
        }
    }
}

/** One tappable recent-day row — opens the Day Log editor for that day. testTag `log.editDay.<iso>`. */
@Composable
private fun RecentDayRow(entry: DayEntry, onEditDay: (Long) -> Unit) {
    val colors = LocalBloomColors.current
    val iso = LocalDate.ofEpochDay(entry.epochDay).toString()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable { onEditDay(entry.epochDay) }
            .testTag("log.editDay.$iso")
            .semantics { role = Role.Button; contentDescription = iso }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = iso, style = bloomBody, color = colors.ink)
    }
}

/** The honest empty state shown when no days are logged yet. */
@Composable
private fun EmptyState() {
    val colors = LocalBloomColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bloomCard()
            .testTag("log.emptyState"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = TrackingCopy.EMPTY_TITLE, style = bloomH3, color = colors.ink)
        Text(text = TrackingCopy.EMPTY_BODY, style = bloomBody, color = colors.inkSecondary)
    }
}
