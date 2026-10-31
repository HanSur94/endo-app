// EndoApp (:app) — the fast Daily Check-In screen (ATRK-01, Plan 20-02). A single vertical scroll of
// Bloom form sections (Same as yesterday → Symptoms → Food → Activity → Sleep → Medication → Stress →
// Mood → Bleeding) where NOTHING is mandatory. "Done for today" maps the nil-honest [DayDraft] through
// [DayDraftMapper.toRows] and persists it via the injected consent-gated seam, fires the fire-and-forget
// reminder auto-suppress ([onDayLogged]), and dismisses — dismissal is NEVER gated on persistence or on
// the reminder side effect (threat T-20-02c). An EMPTY confirm records ONLY the DayEntry adherence
// marker (the mapper emits no symptom/factor/bleeding rows) and shows an honest inline note.
//
// SEPARATION INVARIANT: this file imports NO notification / alarm / WorkManager API — the reminder
// package (Plan 05) owns all of that behind the [onDayLogged] callback seam.
package com.endoapp.app.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.CheckInCopy
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.insights.SymptomType
import kotlinx.coroutines.launch

/** How long the empty-confirm adherence note stays visible before the screen dismisses. */
private const val CONFIRM_NOTE_MILLIS = 900L

/**
 * The Daily Check-In flow. Persistence is injected as seams so the screen stays testable off a fake
 * repository and imports no DAO:
 *
 * @param today the epoch-day the check-in records against.
 * @param loadYesterday reads yesterday's full draft (the sticky-context source), or `null` if none.
 * @param persistDay writes the mapped [DayLogRows] through the consent-gated repository (suspending).
 * @param onDayLogged the fire-and-forget reminder auto-suppress seam (Plan 05) — never blocks dismissal.
 * @param onDismiss pops the check-in off the Log stack.
 * @param idFor a stable per-day id for each row key so re-writing the same day upserts idempotently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyCheckInScreen(
    today: Long,
    loadYesterday: suspend () -> DayDraft?,
    persistDay: suspend (DayLogRows) -> Unit,
    onDayLogged: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    idFor: (String) -> String = { key -> "$today:$key" },
) {
    val colors = LocalBloomColors.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(DayDraft()) }
    var showConfirmNote by remember { mutableStateOf(false) }

    // The empty-confirm note is shown briefly, then the screen dismisses. Dismissal is decoupled from
    // persistence + the reminder side effect (both fired independently in onConfirm).
    LaunchedEffect(showConfirmNote) {
        if (showConfirmNote) {
            kotlinx.coroutines.delay(CONFIRM_NOTE_MILLIS)
            onDismiss()
        }
    }

    fun confirm() {
        val rows = DayDraftMapper.toRows(today, draft, idFor)
        val recordsOnlyMarker = rows.symptoms.isEmpty() && rows.pain.isEmpty() &&
            rows.bleeding.isEmpty() && rows.factors.isEmpty()
        // Persist independently — a slow/failed write must never block dismissal.
        scope.launch { persistDay(rows) }
        // Fire-and-forget reminder auto-suppress (owned by the reminder package; no API imported here).
        onDayLogged()
        if (recordsOnlyMarker) showConfirmNote = true else onDismiss()
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text(TrackingCopy.CHECK_IN_CTA, style = bloomBody, color = colors.ink) },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .testTag("checkin.cancel")
                            .semantics { contentDescription = CheckInCopy.CANCEL },
                    ) {
                        Icon(imageVector = backGlyph, contentDescription = null, tint = colors.ink)
                    }
                },
                actions = {
                    TextButton(onClick = { confirm() }, modifier = Modifier.testTag("checkin.done")) {
                        Text(CheckInCopy.DONE, style = bloomButtonLabel, color = colors.plum)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.canvas),
            )
        },
        containerColor = colors.canvas,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SameAsYesterdaySection(onApply = {
                scope.launch { loadYesterday()?.let { draft = applySameAsYesterday(draft, it) } }
            })
            SymptomsSection(draft, { draft = it })
            if (draft.symptoms.keys.any { it == SymptomType.PELVIC_PAIN || it == SymptomType.BACK_LEG_PAIN } ||
                draft.pain.isNotEmpty()
            ) {
                PainAreasSection(draft, { draft = it })
            }
            FoodSection(draft, { draft = it })
            ActivitySection(draft, { draft = it })
            SleepSection(draft, { draft = it })
            MedicationSection(draft, { draft = it })
            StressSection(draft, { draft = it })
            MoodSection(draft, { draft = it })
            BleedingSection(draft, { draft = it })

            if (showConfirmNote) {
                Text(
                    text = CheckInCopy.CHECKED_IN_NOTE,
                    style = bloomBody,
                    color = colors.inkSecondary,
                    modifier = Modifier.testTag("checkin.confirmNote"),
                )
            }
        }
    }
}

/** A bundled left-arrow glyph (asset-light, mirrors the Bloom build-in-code ethos). */
private val backGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "BloomBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 11f); horizontalLineTo(7.83f)
            lineTo(13.42f, 5.41f); lineTo(12f, 4f); lineTo(4f, 12f); lineTo(12f, 20f)
            lineTo(13.41f, 18.59f); lineTo(7.83f, 13f); horizontalLineTo(20f); close()
        }
    }.build()
}
