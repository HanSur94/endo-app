// EndoApp (:app) — the Daily Check-In section composables (ATRK-01, Plan 20-02), built on the shared
// [TrackingSectionRows] primitives + [SeverityControl] and the registered [CheckInCopy]/[TrackingCopy]
// vocabularies. NOTHING here is mandatory: every section edits the nil-honest [DayDraft] and leaves an
// untouched optional at its absence-encoded default (a skipped field is `null`, never a phantom 0).
//
// The load-bearing honesty seam is [applySameAsYesterday]: it copies ONLY the sticky CONTEXT (meds,
// sleep, activity) from yesterday and NEVER touches symptoms, flow, or clots — a pure function so the
// contract is pinned by a fast JVM test (SameAsYesterdayTest) with no Compose/emulator.
//
// This file imports NO notification / alarm / WorkManager API — the reminder auto-suppress is a
// fire-and-forget callback owned by the host (separation invariant, threat T-20-02c).
package com.endoapp.app.ui.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.endoapp.app.content.CheckInCopy
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.designsystem.EyebrowLabel
import com.endoapp.app.designsystem.LocalBloomColors
import com.endoapp.app.designsystem.bloomBody
import com.endoapp.app.designsystem.bloomCard
import com.endoapp.app.designsystem.bloomH3
import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.FoodTag
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType

/**
 * Apply yesterday's STICKY CONTEXT onto [current] — the "Same as yesterday" contract. Copies ONLY the
 * carried-over context fields (medications taken + as-needed flag, sleep hours/quality, activity
 * type/minutes/intensity) from [yesterday]; symptoms, pain, flow, clots, food, stress, mood, and the
 * note are LEFT EXACTLY as they are in [current] (never fabricated from yesterday). Pure — no side
 * effects — so the honesty contract is unit-testable without Compose.
 */
fun applySameAsYesterday(current: DayDraft, yesterday: DayDraft): DayDraft = current.copy(
    // Sticky context — carried over.
    medsTaken = yesterday.medsTaken,
    tookPRN = yesterday.tookPRN,
    sleepHours = yesterday.sleepHours,
    sleepHoursSet = yesterday.sleepHoursSet,
    sleepQuality = yesterday.sleepQuality,
    activityType = yesterday.activityType,
    activityMinutes = yesterday.activityMinutes,
    activityIntensity = yesterday.activityIntensity,
    // symptoms / pain / flow / clots / foodTags / foodNote / stress / mood / note: UNTOUCHED.
)

/** A Bloom form section — an uppercased header over a card holding [content]. */
@Composable
fun CheckInSection(
    header: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().bloomCard(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EyebrowLabel(header)
        content()
    }
}

/**
 * The "Same as yesterday" prefill row — label + the sticky-context-only subtitle + a tap that asks the
 * host to load yesterday and apply [applySameAsYesterday]. Never fabricates symptoms/bleeding.
 */
@Composable
fun SameAsYesterdaySection(onApply: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBloomColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bloomCard()
            .sizeIn(minHeight = 48.dp)
            .clickable(onClick = onApply)
            .testTag("checkin.sameAsYesterday")
            .semantics { role = Role.Button; contentDescription = CheckInCopy.SAME_AS_YESTERDAY },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = CheckInCopy.SAME_AS_YESTERDAY, style = bloomH3, color = colors.ink)
        Text(text = CheckInCopy.SAME_AS_YESTERDAY_SUBTITLE, style = bloomBody, color = colors.inkSecondary)
    }
}

/** Symptoms — a multi-select; each selected symptom reveals a [SeverityControl]. */
@Composable
fun SymptomsSection(draft: DayDraft, onDraft: (DayDraft) -> Unit, modifier: Modifier = Modifier) {
    CheckInSection(CheckInCopy.SECTION_SYMPTOMS, modifier) {
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
                testTag = "checkin.symptom.${type.raw}",
            )
            if (selected) {
                SeverityControl(
                    value = draft.symptoms[type] ?: 0,
                    onValueChange = { sev ->
                        onDraft(draft.copy(symptoms = draft.symptoms.toMutableMap().apply { put(type, sev) }))
                    },
                    modifier = Modifier.testTag("checkin.symptom.${type.raw}.severity"),
                )
            }
        }
    }
}

/** Food & drink — a multi-select tag row + a free-text note. */
@Composable
fun FoodSection(draft: DayDraft, onDraft: (DayDraft) -> Unit, modifier: Modifier = Modifier) {
    CheckInSection(CheckInCopy.SECTION_FOOD, modifier) {
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
                testTag = "checkin.food.${tag.raw}",
            )
        }
        FreeTextRow(
            value = draft.foodNote.orEmpty(),
            onValueChange = { onDraft(draft.copy(foodNote = it.ifBlank { null })) },
            placeholder = CheckInCopy.FOOD_NOTE_PLACEHOLDER,
            testTag = "checkin.food.note",
            imeAction = ImeAction.Done,
        )
    }
}

/** Activity — single-select type; a chosen type gates the duration stepper + intensity picker. */
@Composable
fun ActivitySection(draft: DayDraft, onDraft: (DayDraft) -> Unit, modifier: Modifier = Modifier) {
    CheckInSection(CheckInCopy.SECTION_ACTIVITY, modifier) {
        ActivityType.entries.forEach { type ->
            val selected = draft.activityType == type
            SingleSelectRow(
                label = TrackingCopy.activityTypeLabels.getValue(type),
                selected = selected,
                onClick = {
                    // Re-tap clears the whole activity unit (type + minutes + intensity → not logged).
                    onDraft(
                        if (selected) {
                            draft.copy(activityType = null, activityMinutes = null, activityIntensity = null)
                        } else {
                            draft.copy(activityType = type)
                        },
                    )
                },
                testTag = "checkin.activity.type.${type.raw}",
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
                testTag = "checkin.activity.duration",
            )
            val intensities = ActivityIntensity.entries
            SegmentedPickerRow(
                label = TrackingCopy.INTENSITY,
                options = intensities,
                optionLabel = { TrackingCopy.activityIntensityLabels.getValue(it) },
                selectedIndex = draft.activityIntensity?.let(intensities::indexOf),
                onSelect = { idx -> onDraft(draft.copy(activityIntensity = idx?.let { intensities[it] })) },
                testTagPrefix = "checkin.activity.intensity",
            )
        }
    }
}

/** Sleep — an hours stepper (honest: unset until touched) + a quality picker. */
@Composable
fun SleepSection(draft: DayDraft, onDraft: (DayDraft) -> Unit, modifier: Modifier = Modifier) {
    CheckInSection(CheckInCopy.SECTION_SLEEP, modifier) {
        StepperRow(
            label = TrackingCopy.HOURS,
            valueText = if (draft.sleepHoursSet) draft.sleepHours?.let { trimHours(it) } else null,
            onDecrement = {
                val next = ((draft.sleepHours ?: 0.0) - 0.5).coerceAtLeast(0.0)
                onDraft(draft.copy(sleepHours = next, sleepHoursSet = true))
            },
            onIncrement = {
                onDraft(draft.copy(sleepHours = (draft.sleepHours ?: 0.0) + 0.5, sleepHoursSet = true))
            },
            testTag = "checkin.sleep.hours",
        )
        val qualities = SleepQuality.entries
        SegmentedPickerRow(
            label = TrackingCopy.QUALITY,
            options = qualities,
            optionLabel = { TrackingCopy.sleepQualityLabels.getValue(it) },
            selectedIndex = draft.sleepQuality?.let(qualities::indexOf),
            onSelect = { idx -> onDraft(draft.copy(sleepQuality = idx?.let { qualities[it] })) },
            testTagPrefix = "checkin.sleep.quality",
        )
    }
}

/**
 * Medication — the as-needed pain-relief flag (per-med "taken" toggles arrive when Settings/manage-meds
 * lands in Plan 04; no saved medications can exist before then).
 */
@Composable
fun MedicationSection(draft: DayDraft, onDraft: (DayDraft) -> Unit, modifier: Modifier = Modifier) {
    CheckInSection(CheckInCopy.SECTION_MEDICATION, modifier) {
        MultiSelectRow(
            label = CheckInCopy.PRN,
            selected = draft.tookPRN,
            onToggle = { onDraft(draft.copy(tookPRN = !draft.tookPRN)) },
            testTag = "checkin.med.prn",
        )
    }
}

/** Stress — reveal-on-opt-in; the draft stays null until the user opts in (absence ≠ 0). */
@Composable
fun StressSection(draft: DayDraft, onDraft: (DayDraft) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBloomColors.current
    CheckInSection(CheckInCopy.SECTION_STRESS, modifier) {
        RevealOnOptInRow(
            label = CheckInCopy.STRESS_LOG,
            optedIn = draft.stress != null,
            onOptIn = { onDraft(draft.copy(stress = 0)) },
            testTag = "checkin.stress",
        ) {
            SeverityControl(
                value = draft.stress ?: 0,
                onValueChange = { onDraft(draft.copy(stress = it)) },
                modifier = Modifier.testTag("checkin.stress.severity"),
            )
            Text(
                text = CheckInCopy.STRESS_CLEAR,
                style = bloomBody,
                color = colors.plum,
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .clickable { onDraft(draft.copy(stress = null)) }
                    .testTag("checkin.stress.clear")
                    .semantics { role = Role.Button; contentDescription = CheckInCopy.STRESS_CLEAR }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** Overall mood — the 5-item scale; nil until tapped. */
@Composable
fun MoodSection(draft: DayDraft, onDraft: (DayDraft) -> Unit, modifier: Modifier = Modifier) {
    CheckInSection(CheckInCopy.SECTION_MOOD, modifier) {
        MoodRow(
            selected = draft.mood,
            onSelect = { value -> onDraft(draft.copy(mood = if (draft.mood == value) null else value)) },
            testTagPrefix = "checkin.mood",
        )
    }
}

/** Bleeding — single-select flow; a chosen flow reveals the clots toggle. Re-tap clears the flow. */
@Composable
fun BleedingSection(draft: DayDraft, onDraft: (DayDraft) -> Unit, modifier: Modifier = Modifier) {
    CheckInSection(CheckInCopy.SECTION_BLEEDING, modifier) {
        FlowIntensity.entries.forEach { flow ->
            val selected = draft.flow == flow
            SingleSelectRow(
                label = TrackingCopy.flowIntensityLabels.getValue(flow),
                selected = selected,
                onClick = {
                    // Re-tap clears bleeding entirely (flow + clots → not recorded).
                    onDraft(if (selected) draft.copy(flow = null, clots = null) else draft.copy(flow = flow))
                },
                testTag = "checkin.flow.${flow.raw}",
            )
        }
        if (draft.flow != null) {
            MultiSelectRow(
                label = CheckInCopy.CLOTS,
                selected = draft.clots == true,
                onToggle = { onDraft(draft.copy(clots = if (draft.clots == true) false else true)) },
                testTag = "checkin.clots",
            )
        }
    }
}

/** Render sleep hours without a trailing ".0" (e.g. 7.0 → "7", 7.5 → "7.5"). */
private fun trimHours(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()
