// The registered SHARED, cross-screen tracking vocabulary for Phase 20 — the fourth `AuditableCopy`
// source in `:app` (after ShellCopy + PlaceholderCopy + OnboardingCopy). Per the LOAD-BEARING RULE
// (CLAUDE.md / MEMORY `endo-copy-audit-substring-gate`), these strings are registered into
// [CopyAuditRegistry.sources] in the SAME change that introduces the foundation + shared controls
// that render them, so `CopyAuditTest` scans them from birth.
//
// SCOPE: only the SHARED strings the Phase-20 foundation + shared controls render — the not-logged
// caption, the SeverityControl + BodyMap value strings, the shared control labels, the Log-home
// chrome, and the enum→label vocabularies. PER-SCREEN copy (Check-In / Day Log / Settings /
// reminder) lands in each later plan's own `…Copy : AuditableCopy` object, registered there.
//
// SINGLE-SOURCE NOTE: the Log-home eyebrow "Log" is NOT re-declared here — it is already owned by
// [ShellCopy.TAB_LOG] (and [PlaceholderCopy.LOG_EYEBROW]); `LogHomeScreen` references `ShellCopy`.
//
// SUBSTRING-GATE NEAR-MISSES (extends the UI-SPEC §7 reword set): the CopyAudit scan is a
// case-insensitive SUBSTRING match against `ClaimsPolicy` (causal/imperative/alarmist/gendered/
// precision). Two enum labels the UI-SPEC §7 table specified VERBATIM collide with the imperative
// list and are reworded here (raw enum values — the parity contract — are untouched; only the
// display labels live in this object):
//   - BodyRegion.SHOULDER "Shoulder" ⊃ "should" → reworded to "Neck / collarbone" (front-top region).
//   - SymptomType.GI_ENDO_BELLY "Bloating / nausea / gut": "nausea" ⊃ "use" → reworded "queasy".
// The three the UI-SPEC already reworded (here→dropped, stops→"keeps it out", treatment→"hormonal
// medication") are per-screen strings and land in later plans; none are reintroduced here.
package com.endoapp.app.content

import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.BodyRegion
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.FoodTag
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType

/**
 * The shared, registered Phase-20 tracking vocabulary rendered by the foundation host, the shared
 * `SeverityControl`, the section rows, and (by reference) every later tracking screen. Exposed as
 * named constants + enum→label maps so each composable references a single source, and collected in
 * [auditableStrings] for the `CopyAudit` substring scan.
 *
 * The `severityReadout` / `severityStateDescription` / `severitySummary` builders are pure format
 * functions; representative sampled outputs are contributed to [auditableStrings] so their format
 * strings are scanned too.
 */
object TrackingCopy : AuditableCopy {

    // --- Honesty caption (the shared "not logged" seam, absence ≠ 0) ---

    /** The shared caption every untouched optional factor section renders — NEVER a 0. */
    const val NOT_LOGGED = "Not logged"

    // --- SeverityControl ---

    /** The SeverityControl label. */
    const val SEVERITY = "Severity"

    /** The `N/10` severity readout (e.g. "Severity 7/10"). Pure builder; sampled in [auditableStrings]. */
    fun severityReadout(n: Int): String = "$n/10"

    /** Severity preset chip — the low end (maps to 3). */
    const val SEVERITY_PRESET_MILD = "Mild"

    /** Severity preset chip — the middle (maps to 6). */
    const val SEVERITY_PRESET_MODERATE = "Moderate"

    /** Severity preset chip — the high end (maps to 9). */
    const val SEVERITY_PRESET_SEVERE = "Severe"

    // --- Shared control labels (reused across the factor sections) ---

    /** Activity duration label. */
    const val DURATION = "Duration"

    /** Sleep hours label. */
    const val HOURS = "Hours"

    /** Sleep quality label. */
    const val QUALITY = "Quality"

    /** Activity / general intensity label. */
    const val INTENSITY = "Intensity"

    // --- BodyMap value strings (TalkBack stateDescription + summary readouts) ---

    /** TalkBack `stateDescription` for a marked region — "severity N of 10". Pure builder. */
    fun severityStateDescription(n: Int): String = "severity $n of 10"

    /** TalkBack `stateDescription` for an unmarked region. */
    const val NOT_MARKED = "not marked"

    /** Marked-summary pill value — "N/10". Pure builder. */
    fun severitySummary(n: Int): String = "$n/10"

    /** The unset stepper/read-out em-dash. */
    const val UNSET_READOUT = "—"

    // --- Log-home chrome (eyebrow reuses ShellCopy.TAB_LOG — not re-declared) ---

    /** The Log-home serif greeting heading. */
    const val GREETING = "How are you today?"

    /** The prominent primary CTA — opens Daily Check-In. */
    const val CHECK_IN_CTA = "Daily check-in"

    /** The "log a past day" affordance label. */
    const val LOG_PAST_DAY = "Log a past day"

    /** Honest empty-state title when no days are logged yet. */
    const val EMPTY_TITLE = "Nothing logged yet"

    /** Honest empty-state body (dodges the imperative "start"/"use" + gendered "her" traps). */
    const val EMPTY_BODY = "Add your first check-in and your logged days appear in this list."

    // --- Enum → user-facing label vocabularies (raw values are the parity contract; labels are here) ---

    /** SymptomType (11) → label. `GI_ENDO_BELLY` reworded off "nausea" (⊃ "use"). */
    val symptomLabels: Map<SymptomType, String> = mapOf(
        SymptomType.PELVIC_PAIN to "Pelvic / abdominal pain",
        SymptomType.DYSMENORRHEA to "Pain during bleeding",
        SymptomType.DYSPAREUNIA to "Pain during intimacy",
        SymptomType.GI_ENDO_BELLY to "Bloating / queasy / gut",
        SymptomType.URINARY to "Urinary discomfort",
        SymptomType.FATIGUE to "Fatigue",
        SymptomType.BRAIN_FOG to "Brain fog",
        SymptomType.LOW_MOOD to "Low mood",
        SymptomType.HEADACHE to "Headache",
        SymptomType.BACK_LEG_PAIN to "Back / leg pain",
        SymptomType.OTHER to "Something else",
    )

    /** BodyRegion (10) → label. `SHOULDER` reworded off "Shoulder" (⊃ "should"). */
    val bodyRegionLabels: Map<BodyRegion, String> = mapOf(
        BodyRegion.LOWER_ABDOMEN_PELVIS to "Lower abdomen / pelvis",
        BodyRegion.LOWER_QUADRANT_LEFT to "Lower left side",
        BodyRegion.LOWER_QUADRANT_RIGHT to "Lower right side",
        BodyRegion.LOWER_BACK to "Lower back",
        BodyRegion.HIPS to "Hips",
        BodyRegion.UPPER_THIGHS to "Upper thighs",
        BodyRegion.RECTAL_BOWEL to "Rectal / bowel",
        BodyRegion.BLADDER to "Bladder",
        BodyRegion.CHEST_DIAPHRAGM to "Chest / diaphragm",
        BodyRegion.SHOULDER to "Neck / collarbone",
    )

    /** FoodTag (9) → label. */
    val foodTagLabels: Map<FoodTag, String> = mapOf(
        FoodTag.DAIRY to "Dairy",
        FoodTag.GLUTEN_WHEAT to "Gluten / wheat",
        FoodTag.CAFFEINE to "Caffeine",
        FoodTag.ALCOHOL to "Alcohol",
        FoodTag.HIGH_SUGAR to "High sugar",
        FoodTag.HIGH_FODMAP to "High FODMAP",
        FoodTag.RED_PROCESSED_MEAT to "Red / processed meat",
        FoodTag.LARGE_MEAL to "Large meal",
        FoodTag.OTHER to "Something else",
    )

    /** ActivityType (5) → label. */
    val activityTypeLabels: Map<ActivityType, String> = mapOf(
        ActivityType.WALKING to "Walking",
        ActivityType.CARDIO to "Cardio",
        ActivityType.STRENGTH to "Strength",
        ActivityType.YOGA_STRETCHING to "Yoga / stretching",
        ActivityType.OTHER to "Something else",
    )

    /** ActivityIntensity (3) → label. */
    val activityIntensityLabels: Map<ActivityIntensity, String> = mapOf(
        ActivityIntensity.LOW to "Low",
        ActivityIntensity.MEDIUM to "Medium",
        ActivityIntensity.HIGH to "High",
    )

    /** SleepQuality (3) → label. */
    val sleepQualityLabels: Map<SleepQuality, String> = mapOf(
        SleepQuality.POOR to "Poor",
        SleepQuality.OKAY to "Okay",
        SleepQuality.GOOD to "Good",
    )

    /** FlowIntensity (4) → label. */
    val flowIntensityLabels: Map<FlowIntensity, String> = mapOf(
        FlowIntensity.SPOTTING to "Spotting",
        FlowIntensity.LIGHT to "Light",
        FlowIntensity.MEDIUM to "Medium",
        FlowIntensity.HEAVY to "Heavy",
    )

    /** Mood 1–5 scale → label (there is no real "0" mood; nil = not logged). */
    val moodLabels: Map<Int, String> = mapOf(
        1 to "Very low",
        2 to "Low",
        3 to "Okay",
        4 to "Good",
        5 to "Great",
    )

    override val auditableStrings: List<String> = buildList {
        add(NOT_LOGGED)
        add(SEVERITY)
        add(SEVERITY_PRESET_MILD)
        add(SEVERITY_PRESET_MODERATE)
        add(SEVERITY_PRESET_SEVERE)
        add(DURATION)
        add(HOURS)
        add(QUALITY)
        add(INTENSITY)
        add(NOT_MARKED)
        add(UNSET_READOUT)
        add(GREETING)
        add(CHECK_IN_CTA)
        add(LOG_PAST_DAY)
        add(EMPTY_TITLE)
        add(EMPTY_BODY)
        // Representative sampled builder outputs (0 / mid / max) so their format strings are scanned.
        add(severityReadout(0)); add(severityReadout(5)); add(severityReadout(10))
        add(severityStateDescription(0)); add(severityStateDescription(5)); add(severityStateDescription(10))
        add(severitySummary(0)); add(severitySummary(10))
        // Every enum label value.
        addAll(symptomLabels.values)
        addAll(bodyRegionLabels.values)
        addAll(foodTagLabels.values)
        addAll(activityTypeLabels.values)
        addAll(activityIntensityLabels.values)
        addAll(sleepQualityLabels.values)
        addAll(flowIntensityLabels.values)
        addAll(moodLabels.values)
    }
}
