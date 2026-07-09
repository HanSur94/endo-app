// EndoInsights (:insights) — the package-local snapshot-facing enums.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/SnapshotEnums.swift`. These are
// pure-JVM value enums (no android./androidx.). Each Kotlin case name may be idiomatic
// UPPER_SNAKE, but the `raw: String` is LOAD-BEARING: it equals the Swift case name VERBATIM
// (the Swift enums are `enum X: String` with no explicit `= "..."`, so raw == case name). That
// raw string is the parity contract — it feeds `signalKey`, Room columns, and export JSON, and
// is drift-guarded by EnumRawValueParityTests + enum-raw-values.golden.json.
//
// HONESTY / robustness: each enum carries the SAME defensive decode as its Swift counterpart —
// unknown/null sinks to `.other` (`fromRaw`) for the catalogs with an `.other` sink, or to `null`
// (`fromRawOrNull`) for the optional ones. A corrupt stored raw never throws (ASVS V5).
package com.endoapp.insights

/** The endo-specific symptom catalog (mirrors app `SymptomType`, raw values verbatim). */
enum class SymptomType(val raw: String) {
    PELVIC_PAIN("pelvicPain"),
    DYSMENORRHEA("dysmenorrhea"),
    DYSPAREUNIA("dyspareunia"),
    GI_ENDO_BELLY("giEndoBelly"),
    URINARY("urinary"),
    FATIGUE("fatigue"),
    BRAIN_FOG("brainFog"),
    LOW_MOOD("lowMood"),
    HEADACHE("headache"),
    BACK_LEG_PAIN("backLegPain"),
    OTHER("other");

    companion object {
        /** Defensive decode: an unknown or `null` raw maps to `OTHER` (the free-text sink), never throws. */
        fun fromRaw(raw: String?): SymptomType =
            entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

/** The curated named body-map regions (mirrors app `BodyRegion`, raw values verbatim). */
enum class BodyRegion(val raw: String) {
    LOWER_ABDOMEN_PELVIS("lowerAbdomenPelvis"),
    LOWER_QUADRANT_LEFT("lowerQuadrantLeft"),
    LOWER_QUADRANT_RIGHT("lowerQuadrantRight"),
    LOWER_BACK("lowerBack"),
    HIPS("hips"),
    UPPER_THIGHS("upperThighs"),
    RECTAL_BOWEL("rectalBowel"),
    BLADDER("bladder"),
    CHEST_DIAPHRAGM("chestDiaphragm"),
    SHOULDER("shoulder");

    companion object {
        /** Defensive decode: an unknown or `null` raw returns `null` (caller: "not marked"), never throws. */
        fun fromRawOrNull(raw: String?): BodyRegion? =
            raw?.let { r -> entries.firstOrNull { it.raw == r } }
    }
}

/** Bleeding/flow intensity (mirrors app `FlowIntensity`, raw values verbatim). */
enum class FlowIntensity(val raw: String) {
    SPOTTING("spotting"),
    LIGHT("light"),
    MEDIUM("medium"),
    HEAVY("heavy");

    companion object {
        /** Defensive decode: an unknown or `null` raw returns `null`, never throws. */
        fun fromRawOrNull(raw: String?): FlowIntensity? =
            raw?.let { r -> entries.firstOrNull { it.raw == r } }
    }
}

/** The curated food/drink category tags (mirrors app `FoodTag`, raw values verbatim). */
enum class FoodTag(val raw: String) {
    DAIRY("dairy"),
    GLUTEN_WHEAT("glutenWheat"),
    CAFFEINE("caffeine"),
    ALCOHOL("alcohol"),
    HIGH_SUGAR("highSugar"),
    HIGH_FODMAP("highFODMAP"),
    RED_PROCESSED_MEAT("redProcessedMeat"),
    LARGE_MEAL("largeMeal"),
    OTHER("other");

    companion object {
        /** Defensive decode: an unknown or `null` raw maps to `OTHER` (the free-text sink), never throws. */
        fun fromRaw(raw: String?): FoodTag =
            entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

/** The curated activity types (mirrors app `ActivityType`, raw values verbatim). */
enum class ActivityType(val raw: String) {
    WALKING("walking"),
    CARDIO("cardio"),
    STRENGTH("strength"),
    YOGA_STRETCHING("yogaStretching"),
    OTHER("other");

    companion object {
        /** Defensive decode: an unknown or `null` raw maps to `OTHER` (the free-text sink), never throws. */
        fun fromRaw(raw: String?): ActivityType =
            entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

/** Perceived activity intensity (mirrors app `ActivityIntensity`, raw values verbatim). */
enum class ActivityIntensity(val raw: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        /** Defensive decode: an unknown or `null` raw returns `null`, never throws. */
        fun fromRawOrNull(raw: String?): ActivityIntensity? =
            raw?.let { r -> entries.firstOrNull { it.raw == r } }
    }
}

/** Subjective sleep quality (mirrors app `SleepQuality`, raw values verbatim). */
enum class SleepQuality(val raw: String) {
    POOR("poor"),
    OKAY("okay"),
    GOOD("good");

    companion object {
        /** Defensive decode: an unknown or `null` raw returns `null`, never throws. */
        fun fromRawOrNull(raw: String?): SleepQuality? =
            raw?.let { r -> entries.firstOrNull { it.raw == r } }
    }
}
