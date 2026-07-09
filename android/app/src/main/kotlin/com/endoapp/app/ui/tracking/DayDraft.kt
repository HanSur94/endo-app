// EndoApp (:app) — the nil-honest per-day editable draft (ATRK-05, the UI-side carry of the
// absence ≠ 0 honesty invariant).
//
// This is the single editable value type every Phase-20 tracking screen (Check-In, Day Log) holds
// while the user edits a day. EVERY optional field is nullable / absence-encoded so the UI can NEVER
// fabricate a value:
//   - a field left untouched is `null` / absent — "not logged", NEVER 0;
//   - a field the user set to 0 (a slid-to-0 severity, a stress 0 after opt-in, 0.0 sleep hours) is a
//     REAL 0, distinct from absent.
// [DayDraftMapper] is the pure seam that persists this distinction through Room rows and back.
//
// The enums are the pure `:insights` catalog (the same raw values persisted + drift-guarded); the
// draft holds domain values, never Room entities.
package com.endoapp.app.ui.tracking

import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.BodyRegion
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.FoodTag
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType

/**
 * The nil-honest per-day draft (UI-SPEC §3 honesty table). Every optional is absence-encoded:
 *
 * - [symptoms] — a key present means the symptom is SELECTED; its `Int?` value `null` means severity
 *   is unspecified (selected, no severity), `0` a real logged 0, `1..10` a real severity. An ABSENT
 *   key means the symptom is not selected (no row at all).
 * - [pain] — same shape keyed by [BodyRegion] (absent = not marked).
 * - [flow] / [clots] — `null` flow = no bleeding logged (clears the day's bleeding); [clots] is a
 *   nullable fact (only meaningful with a flow).
 * - [foodTags] / [foodNote] — an empty set + null note = not logged.
 * - [activityType] / [activityMinutes] / [activityIntensity] — a `null` type = not logged (minutes +
 *   intensity are then also nil); `0.0` minutes WITH a type is a real 0.
 * - [sleepHours] / [sleepHoursSet] / [sleepQuality] — [sleepHoursSet] `false` = hours not logged
 *   (nil at the seam); `0.0` hours WITH [sleepHoursSet] `true` is a real 0.
 * - [medsTaken] — the set of saved-med ids toggled taken; [tookPRN] the as-needed flag.
 * - [stress] — `null` = not opted in (not logged); `0` a real logged 0 (opted in, zero stress).
 * - [mood] — `null` = not logged; `1..5` a real value (no real 0).
 * - [note] — `null` / empty = not logged.
 */
data class DayDraft(
    val symptoms: Map<SymptomType, Int?> = emptyMap(),
    val pain: Map<BodyRegion, Int?> = emptyMap(),
    val flow: FlowIntensity? = null,
    val clots: Boolean? = null,
    val foodTags: Set<FoodTag> = emptySet(),
    val foodNote: String? = null,
    val activityType: ActivityType? = null,
    val activityMinutes: Double? = null,
    val activityIntensity: ActivityIntensity? = null,
    val sleepHours: Double? = null,
    val sleepHoursSet: Boolean = false,
    val sleepQuality: SleepQuality? = null,
    val medsTaken: Set<String> = emptySet(),
    val tookPRN: Boolean = false,
    val stress: Int? = null,
    val mood: Int? = null,
    val note: String? = null,
)
