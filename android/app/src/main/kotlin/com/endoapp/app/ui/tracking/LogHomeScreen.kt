// EndoApp (:app) — the real Log tab home (ATRK-01), replacing the Phase-19 `tab.log` placeholder.
// A prominent Daily-check-in CTA, a settings gear, a recent-days list, and an honest empty state.
// Every string comes from the registered [TrackingCopy] / [ShellCopy]; all targets ≥48dp; the recent
// days arrive as a Flow off the consent-gated repository (the host never touches a DAO directly).
package com.endoapp.app.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.ReportCopy
import com.endoapp.app.content.ShellCopy
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.designsystem.BloomPrimaryButton
import com.endoapp.app.designsystem.EyebrowLabel
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomDisplay
import com.endoapp.app.designsystem.bloomH2
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.app.domain.CycleSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The Log-home content. [recentDays] is the live day history (newest first) off the repository;
 * [onCheckIn], [onEditDay], and [onOpenSettings] push the corresponding [TrackingRoute] on the
 * in-Log stack.
 */
@Composable
fun LogHomeScreen(
    recentDays: List<DayEntry>,
    onCheckIn: () -> Unit,
    onLogToday: () -> Unit,
    onEditDay: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    cycleSummary: CycleSummary? = null,
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

        // The cycle card (iOS Home parity) — shown once the summary has been derived.
        cycleSummary?.let { CycleCard(it) }

        // The prominent primary CTA.
        BloomPrimaryButton(
            text = TrackingCopy.CHECK_IN_CTA,
            onClick = onCheckIn,
            modifier = Modifier.testTag("log.checkInCta"),
        )

        // The always-visible "Log today" entry — the full Day Log editor for today (pain map + all
        // factors) stays reachable even before anything is logged.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bloomCard()
                .sizeIn(minHeight = 56.dp)
                .clickable(onClick = onLogToday)
                .testTag("log.logTodayCta")
                .semantics { role = Role.Button; contentDescription = TrackingCopy.LOG_TODAY_CTA },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "✎", style = bloomBody, color = colors.plum)
            Text(text = TrackingCopy.LOG_TODAY_CTA, style = bloomBody, color = colors.plum)
        }

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
    val date = LocalDate.ofEpochDay(entry.epochDay)
    val label = dayRowLabel(date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable { onEditDay(entry.epochDay) }
            .testTag("log.editDay.$date")
            .semantics { role = Role.Button; contentDescription = label }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = bloomBody, color = colors.ink)
        Text(text = "›", style = bloomBody, color = colors.inkMuted)
    }
}

/** "Fri, Oct 30" in the current year, "Fri, Oct 30, 2025" otherwise — never a raw ISO key. */
private fun dayRowLabel(date: LocalDate): String =
    date.format(if (date.year == LocalDate.now().year) DAY_ROW_THIS_YEAR else DAY_ROW_OTHER_YEAR)

private val DAY_ROW_THIS_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val DAY_ROW_OTHER_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

/**
 * The rose→plum cycle card (iOS `HomeView.cycleCard` + `CycleSummaryView`). Exactly three states and
 * never a phantom cycle: suppressed → only the off line; sparse → "not enough data yet"; derived →
 * cycle day + an estimated next window as a date RANGE + a confidence word.
 */
@Composable
private fun CycleCard(summary: CycleSummary) {
    val colors = LocalBloomColors.current
    val soft = Color.White.copy(alpha = 0.82f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(colors.rose, colors.plum)))
            .padding(20.dp)
            .testTag("log.cycleSummary")
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EyebrowLabel(TrackingCopy.CYCLE_EYEBROW, color = Color.White.copy(alpha = 0.85f))
        when {
            summary.isSuppressed ->
                Text(ReportCopy.CYCLE_SUPPRESSED, style = bloomBody, color = soft, modifier = Modifier.testTag("log.cycleSummary.suppressed"))
            !summary.hasEnoughData ->
                Text(TrackingCopy.CYCLE_NOT_ENOUGH_DATA, style = bloomBody, color = soft, modifier = Modifier.testTag("log.cycleSummary.notEnoughData"))
            else -> {
                summary.cycleDay?.let {
                    Text(ReportCopy.cycleDayValue(it), style = bloomH2, color = Color.White, modifier = Modifier.testTag("log.cycleSummary.cycleDay"))
                }
                summary.nextPeriod?.let { range ->
                    val window = "${CYCLE_RANGE_FORMAT.format(range.start)} – ${CYCLE_RANGE_FORMAT.format(range.endInclusive)}"
                    Text("${ReportCopy.CYCLE_NEXT_WINDOW_LABEL}: $window", style = bloomBody, color = soft, modifier = Modifier.testTag("log.cycleSummary.nextWindow"))
                }
                Text(
                    "${TrackingCopy.cycleConfidenceWord(summary.confidence)} · ${TrackingCopy.CYCLE_BASIS}",
                    style = bloomBody,
                    color = soft,
                )
            }
        }
    }
}

private val CYCLE_RANGE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

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
