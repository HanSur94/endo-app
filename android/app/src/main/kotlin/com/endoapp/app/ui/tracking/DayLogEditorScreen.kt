// EndoApp (:app) — the Day Log editor (ATRK-02, Plan 20-03). A per-day form (Symptoms + severity →
// Pain areas (the accessible BodyMap) → Bleeding → the factor sections → Note) reusing the plan-01
// nil-honest [DayDraft] / [DayDraftMapper] and the shared section-row primitives. NOTHING is
// mandatory: an untouched section stays absence-encoded (a skipped field reads "Not logged", never a
// phantom 0; a slid-to-0 severity is a real 0), and the round trip (load → edit → Save → reload)
// preserves nil-vs-0 exactly through the mapper.
//
// A guarded destructive "Delete this day" appears ONLY when the loaded day already has data; it is
// gated behind a confirmation dialog and delegates to the atomic [EndoRepository.deleteDay] cascade.
//
// SEPARATION INVARIANT: this file imports NO notification / alarm / WorkManager API — the reminder
// auto-suppress is the fire-and-forget [onDayLogged] callback owned by the host (threat T-20-02c).
package com.endoapp.app.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.DayLogCopy
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.designsystem.EyebrowLabel
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomButtonLabel
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.FoodTag
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Day Log editor for [epochDay]. Persistence is injected as seams so the screen stays testable off
 * a fake repository and imports no DAO:
 *
 * @param loadDay reads the existing day's draft, or `null` for a fresh (never-logged) day. A non-null
 *   result marks the day as already-having-data → the guarded delete is offered.
 * @param persistDay writes the mapped [DayLogRows] through the consent-gated repository (suspending).
 * @param deleteDay purges the day via the atomic [EndoRepository][com.endoapp.app.data.EndoRepository]
 *   delete cascade.
 * @param onDismiss pops the editor off the Log stack.
 * @param onDayLogged the fire-and-forget reminder auto-suppress seam (Plan 05) — never blocks dismissal.
 * @param idFor a stable per-day id for each row key so re-writing the same day upserts idempotently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogEditorScreen(
    epochDay: Long,
    loadDay: suspend (Long) -> DayDraft?,
    persistDay: suspend (DayLogRows) -> Unit,
    deleteDay: suspend (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDayLogged: () -> Unit = {},
    idFor: (String) -> String = { key -> "$epochDay:$key" },
) {
    val colors = LocalBloomColors.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(DayDraft()) }
    var dayHasData by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // On open, load the existing day (if any). A fresh day starts empty = every section "Not logged".
    LaunchedEffect(epochDay) {
        loadDay(epochDay)?.let { existing ->
            draft = existing
            dayHasData = true
        }
    }

    fun save() {
        val rows = DayDraftMapper.toRows(epochDay, draft, idFor)
        scope.launch { persistDay(rows) }
        onDayLogged()
        onDismiss()
    }

    Scaffold(
        modifier = modifier.testTag("log.route.dayLog"),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = LocalDate.ofEpochDay(epochDay).toString(),
                        style = bloomH3,
                        color = colors.ink,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .testTag("daylog.cancel")
                            .semantics { contentDescription = DayLogCopy.CANCEL },
                    ) {
                        Icon(imageVector = dayLogBackGlyph, contentDescription = null, tint = colors.ink)
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, modifier = Modifier.testTag("daylog.save")) {
                        Text(DayLogCopy.SAVE, style = bloomButtonLabel, color = colors.plum)
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
            SymptomsCard(draft) { draft = it }
            PainAreasCard(draft) { draft = it }
            BleedingCard(draft) { draft = it }
            FoodCard(draft) { draft = it }
            ActivityCard(draft) { draft = it }
            SleepCard(draft) { draft = it }
            MedicationCard(draft) { draft = it }
            StressCard(draft) { draft = it }
            MoodCard(draft) { draft = it }
            NoteCard(draft) { draft = it }

            // The guarded destructive delete — offered ONLY for a day that already has data.
            if (dayHasData) {
                Text(
                    text = DayLogCopy.DELETE_DAY,
                    style = bloomButtonLabel,
                    color = colors.rose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clickable { showDeleteDialog = true }
                        .testTag("daylog.delete")
                        .semantics { role = Role.Button; contentDescription = DayLogCopy.DELETE_DAY }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            modifier = Modifier.testTag("daylog.deleteDialog"),
            title = { Text(DayLogCopy.DELETE_TITLE, style = bloomH3, color = colors.ink) },
            text = { Text(DayLogCopy.DELETE_BODY, style = bloomBody, color = colors.inkSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch { deleteDay(epochDay) }
                        onDismiss()
                    },
                    modifier = Modifier.testTag("daylog.deleteConfirm"),
                ) { Text(DayLogCopy.DELETE_CONFIRM, style = bloomButtonLabel, color = colors.rose) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag("daylog.deleteCancel"),
                ) { Text(DayLogCopy.CANCEL, style = bloomButtonLabel, color = colors.inkMuted) }
            },
            containerColor = colors.card,
        )
    }
}

// --- Section cards (daylog.* testTags; reuse the shared TrackingSectionRows + SeverityControl) ---

/** A Bloom form section — an uppercased header over a card holding [content]. */
@Composable
private fun DayLogSection(header: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().bloomCard(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EyebrowLabel(header)
        content()
    }
}

/** Symptoms — a multi-select; each selected symptom reveals a [SeverityControl]. */
@Composable
private fun SymptomsCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    DayLogSection(DayLogCopy.SECTION_SYMPTOMS) {
        SymptomType.entries.forEach { type ->
            val selected = draft.symptoms.containsKey(type)
            MultiSelectRow(
                label = TrackingCopy.symptomLabels.getValue(type),
                selected = selected,
                onToggle = {
                    val next = draft.symptoms.toMutableMap()
                    if (selected) next.remove(type) else next[type] = null // selected, no severity yet.
                    onDraft(draft.copy(symptoms = next))
                },
                testTag = "daylog.symptom.${type.raw}",
            )
            if (selected) {
                SeverityControl(
                    value = draft.symptoms[type] ?: 0,
                    onValueChange = { sev ->
                        onDraft(draft.copy(symptoms = draft.symptoms.toMutableMap().apply { put(type, sev) }))
                    },
                    modifier = Modifier.testTag("daylog.symptom.${type.raw}.severity"),
                )
            }
        }
    }
}

/**
 * Pain areas — Task 1 renders a placeholder host; Task 2 mounts the accessible `BodyMapView` here so
 * both the hotspot map and the list fallback write the SAME [DayDraft.pain] point.
 */
@Composable
private fun PainAreasCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    DayLogSection(DayLogCopy.SECTION_PAIN) {
        Box(modifier = Modifier.fillMaxWidth().testTag("daylog.painAreas.host"))
    }
}

/** Bleeding — single-select flow; a chosen flow reveals the clots toggle. Re-tap clears the flow. */
@Composable
private fun BleedingCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    DayLogSection(DayLogCopy.SECTION_BLEEDING) {
        FlowIntensity.entries.forEach { flow ->
            val selected = draft.flow == flow
            SingleSelectRow(
                label = TrackingCopy.flowIntensityLabels.getValue(flow),
                selected = selected,
                onClick = {
                    onDraft(if (selected) draft.copy(flow = null, clots = null) else draft.copy(flow = flow))
                },
                testTag = "daylog.flow.${flow.raw}",
            )
        }
        if (draft.flow != null) {
            MultiSelectRow(
                label = DayLogCopy.CLOTS,
                selected = draft.clots == true,
                onToggle = { onDraft(draft.copy(clots = if (draft.clots == true) false else true)) },
                testTag = "daylog.clots",
            )
        }
    }
}

/** Food & drink — a multi-select tag row + a free-text note + the honest footer. */
@Composable
private fun FoodCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    val colors = LocalBloomColors.current
    DayLogSection(DayLogCopy.SECTION_FOOD) {
        FoodTag.entries.forEach { tag ->
            val selected = draft.foodTags.contains(tag)
            MultiSelectRow(
                label = TrackingCopy.foodTagLabels.getValue(tag),
                selected = selected,
                onToggle = {
                    val next = draft.foodTags.toMutableSet()
                    if (selected) next.remove(tag) else next.add(tag)
                    onDraft(draft.copy(foodTags = next))
                },
                testTag = "daylog.food.${tag.raw}",
            )
        }
        FreeTextRow(
            value = draft.foodNote.orEmpty(),
            onValueChange = { onDraft(draft.copy(foodNote = it.ifBlank { null })) },
            placeholder = DayLogCopy.FOOD_NOTE_PLACEHOLDER,
            testTag = "daylog.food.note",
            imeAction = ImeAction.Done,
        )
        Text(text = DayLogCopy.FOOD_FOOTER, style = bloomBody, color = colors.inkMuted)
    }
}

/** Activity — single-select type; a chosen type gates the duration stepper + intensity picker. */
@Composable
private fun ActivityCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    DayLogSection(DayLogCopy.SECTION_ACTIVITY) {
        ActivityType.entries.forEach { type ->
            val selected = draft.activityType == type
            SingleSelectRow(
                label = TrackingCopy.activityTypeLabels.getValue(type),
                selected = selected,
                onClick = {
                    onDraft(
                        if (selected) {
                            draft.copy(activityType = null, activityMinutes = null, activityIntensity = null)
                        } else {
                            draft.copy(activityType = type)
                        },
                    )
                },
                testTag = "daylog.activity.type.${type.raw}",
            )
        }
        if (draft.activityType != null) {
            StepperRow(
                label = TrackingCopy.DURATION,
                valueText = draft.activityMinutes?.let { "${it.toInt()}" },
                onDecrement = {
                    val next = ((draft.activityMinutes ?: 0.0) - 15).coerceAtLeast(0.0)
                    onDraft(draft.copy(activityMinutes = next))
                },
                onIncrement = { onDraft(draft.copy(activityMinutes = (draft.activityMinutes ?: 0.0) + 15)) },
                testTag = "daylog.activity.duration",
            )
            val intensities = ActivityIntensity.entries
            SegmentedPickerRow(
                label = TrackingCopy.INTENSITY,
                options = intensities,
                optionLabel = { TrackingCopy.activityIntensityLabels.getValue(it) },
                selectedIndex = draft.activityIntensity?.let(intensities::indexOf),
                onSelect = { idx -> onDraft(draft.copy(activityIntensity = idx?.let { intensities[it] })) },
                testTagPrefix = "daylog.activity.intensity",
            )
        }
    }
}

/** Sleep — an hours stepper (honest: unset until touched) + a quality picker. */
@Composable
private fun SleepCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    DayLogSection(DayLogCopy.SECTION_SLEEP) {
        StepperRow(
            label = TrackingCopy.HOURS,
            valueText = if (draft.sleepHoursSet) draft.sleepHours?.let { trimSleepHours(it) } else null,
            onDecrement = {
                val next = ((draft.sleepHours ?: 0.0) - 0.5).coerceAtLeast(0.0)
                onDraft(draft.copy(sleepHours = next, sleepHoursSet = true))
            },
            onIncrement = {
                onDraft(draft.copy(sleepHours = (draft.sleepHours ?: 0.0) + 0.5, sleepHoursSet = true))
            },
            testTag = "daylog.sleep.hours",
        )
        val qualities = SleepQuality.entries
        SegmentedPickerRow(
            label = TrackingCopy.QUALITY,
            options = qualities,
            optionLabel = { TrackingCopy.sleepQualityLabels.getValue(it) },
            selectedIndex = draft.sleepQuality?.let(qualities::indexOf),
            onSelect = { idx -> onDraft(draft.copy(sleepQuality = idx?.let { qualities[it] })) },
            testTagPrefix = "daylog.sleep.quality",
        )
    }
}

/** Medication — the as-needed pain-relief flag (per-med toggles arrive with Settings in Plan 04). */
@Composable
private fun MedicationCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    DayLogSection(DayLogCopy.SECTION_MEDICATION) {
        MultiSelectRow(
            label = DayLogCopy.PRN,
            selected = draft.tookPRN,
            onToggle = { onDraft(draft.copy(tookPRN = !draft.tookPRN)) },
            testTag = "daylog.med.prn",
        )
    }
}

/** Stress — reveal-on-opt-in; the draft stays null until the user opts in (absence ≠ 0). */
@Composable
private fun StressCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    val colors = LocalBloomColors.current
    DayLogSection(DayLogCopy.SECTION_STRESS) {
        RevealOnOptInRow(
            label = DayLogCopy.STRESS_LOG,
            optedIn = draft.stress != null,
            onOptIn = { onDraft(draft.copy(stress = 0)) },
            testTag = "daylog.stress",
        ) {
            SeverityControl(
                value = draft.stress ?: 0,
                onValueChange = { onDraft(draft.copy(stress = it)) },
                modifier = Modifier.testTag("daylog.stress.severity"),
            )
            Text(
                text = DayLogCopy.STRESS_CLEAR,
                style = bloomBody,
                color = colors.plum,
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .clickable { onDraft(draft.copy(stress = null)) }
                    .testTag("daylog.stress.clear")
                    .semantics { role = Role.Button; contentDescription = DayLogCopy.STRESS_CLEAR }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** Overall mood — the 5-item scale; nil until tapped. */
@Composable
private fun MoodCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    DayLogSection(DayLogCopy.SECTION_MOOD) {
        MoodRow(
            selected = draft.mood,
            onSelect = { value -> onDraft(draft.copy(mood = if (draft.mood == value) null else value)) },
            testTagPrefix = "daylog.mood",
        )
    }
}

/** Note — a multi-line free-text day note (empty → nil at the seam). */
@Composable
private fun NoteCard(draft: DayDraft, onDraft: (DayDraft) -> Unit) {
    DayLogSection(DayLogCopy.SECTION_NOTE) {
        FreeTextRow(
            value = draft.note.orEmpty(),
            onValueChange = { onDraft(draft.copy(note = it.ifBlank { null })) },
            placeholder = DayLogCopy.NOTE_PLACEHOLDER,
            testTag = "daylog.note",
            imeAction = ImeAction.Done,
        )
    }
}

/** Render sleep hours without a trailing ".0" (e.g. 7.0 → "7", 7.5 → "7.5"). */
private fun trimSleepHours(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()

/** A bundled left-arrow glyph (asset-light, mirrors the Bloom build-in-code ethos). */
private val dayLogBackGlyph: ImageVector by lazy {
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
