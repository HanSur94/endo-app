// The READ-ONLY Health Connect permission set (Phase 21, AHC-02 / AHC-04). This is the code-side
// mirror of the manifest <uses-permission> block: the app declares EXACTLY the four health READ
// permissions plus READ_HEALTH_DATA_HISTORY, and DECLARES ZERO write permission of any kind. The
// read-only invariant is the phase's strongest artifact — auditable in both the manifest
// (ManifestPermissionsTest) and here.
package com.endoapp.app.health

/**
 * The four health READ permission strings + the history permission the app requests via the Health
 * Connect permission contract (`PermissionController.createRequestPermissionResultContract`).
 *
 * READ-ONLY INVARIANT: this object exposes ONLY `READ_*` strings and [HISTORY]. It declares NO
 * `WRITE_*` permission, NO `READ_HEALTH_DATA_IN_BACKGROUND`, and NO networking permission — the app
 * never writes health data back and never talks to the network (mirrors iOS, which took read-only
 * HealthKit authorization). The exact strings are pinned so a future edit that adds a write scope is
 * a visible diff, cross-checked by `ManifestPermissionsTest`.
 *
 * A single `READ_MENSTRUATION` grant gates BOTH `MenstruationFlowRecord` (per-day flow intensity)
 * AND `MenstruationPeriodRecord` (the period span) — one permission, two record types.
 */
object HealthConnectPermissions {

    /** Menstruation flow + period span (both gated by this one permission). */
    const val READ_MENSTRUATION = "android.permission.health.READ_MENSTRUATION"

    /** Sleep sessions (`SleepSessionRecord`). */
    const val READ_SLEEP = "android.permission.health.READ_SLEEP"

    /** Exercise sessions (`ExerciseSessionRecord`). */
    const val READ_EXERCISE = "android.permission.health.READ_EXERCISE"

    /** Nutrition / dietary energy (`NutritionRecord`) — presence-only "ate that day". */
    const val READ_NUTRITION = "android.permission.health.READ_NUTRITION"

    /**
     * Longitudinal history (AHC-04). Without it, Health Connect caps a pre-grant read to ~30 days,
     * silently truncating a multi-year cycle history — the wrong outcome for a longitudinal tracker.
     * Requested AND labelled honestly in the UI (Plan 04 window-label copy). ASSUMPTION A6: requesting
     * history is the chosen AHC-04 approach (the app's value is longitudinal patterns); confirm current
     * history/30-day semantics against developer.android.com/health-and-fitness/health-connect/read-data.
     */
    const val HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    /** Exactly the four READ permissions — no write, no background, no network. */
    val READ: Set<String> = setOf(
        READ_MENSTRUATION,
        READ_SLEEP,
        READ_EXERCISE,
        READ_NUTRITION,
    )

    /** The full set requested at the permission contract: the four READs + history (five total). */
    val REQUESTED: Set<String> = READ + HISTORY
}
