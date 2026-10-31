// Requirement: AREL-03 (Phase 25, threat T-25-01) — the Google Play "Data safety" form answers as a
// committed, machine-checkable artifact.
//
// WHY ALL-"No" IS TRUE (not marketing spin — the corroborated truth):
//   - Endo-App accesses/processes everything ON-DEVICE ONLY. Play's own guidance is explicit that data
//     which is processed locally and NEVER transmitted off the device is NOT "collected" and NOT
//     "shared" — so the top-level "Does your app collect or share any of the required user data types?"
//     is answered "No", and there is no data-type list to enumerate
//     [VERIFIED: support.google.com/googleplay/android-developer/answer/10787469].
//   - The codebase corroborates this and CANNOT drift from it: there is NO `android.permission.INTERNET`
//     (nothing can transmit off-device — the strongest single artifact, guarded since Phase 19), NO
//     analytics/auth/backend/networking coordinate in `gradle/libs.versions.toml` (nothing can phone
//     home — guarded since Phase 17), and NO health WRITE permission (read-only Health Connect, Phase
//     21). Storage is local-only; the ONLY egress is a user-initiated, user-encrypted export the user
//     performs themselves (Phase 23) — a user-initiated share is not "sharing" in the Data-safety sense.
//
// DRIFT-PROOF: `DataSafetyManifestCrossCheckTest` binds these constants to those exact codebase facts —
// if a future INTERNET permission, analytics coordinate, or health WRITE appears (or if these flags are
// flipped to `true` without a real collection path), the cross-check goes RED. The form can never
// silently contradict the code.
package com.endoapp.app.content

/**
 * The committed Google Play Data-safety form answers (AREL-03). Single source of truth read by both
 * [com.endoapp.app.content.DataSafetyManifestCrossCheckTest] (the codebase cross-check) and the
 * `DATA-SAFETY.md` ready-to-paste Console doc (Plan 03).
 *
 * All-"No", empty type lists — TRUE and corroborated: no INTERNET, no analytics, no health WRITE,
 * on-device-only storage with a user-initiated encrypted export (support.google.com/10787469).
 */
object DataSafetyDeclaration {

    /**
     * "Does your app collect any of the required user data types?" → **No.** On-device-only
     * access/processing is NOT "collection" per Play (support.google.com/10787469); there is no
     * INTERNET permission and no analytics/backend coordinate that could transmit data off-device.
     */
    const val collectsData: Boolean = false

    /**
     * "Does your app share any of the required user data types with third parties?" → **No.** There is
     * no third-party SDK, no backend, and no network path; the only egress is a user-initiated,
     * user-encrypted export the user performs themselves (not "sharing").
     */
    const val sharesData: Boolean = false

    /**
     * The data types declared as COLLECTED — empty, consistent with [collectsData] == false. There is
     * no data-type to enumerate because nothing is collected in Play's sense.
     */
    val dataTypesCollected: List<String> = emptyList()

    /**
     * The data types declared as SHARED — empty, consistent with [sharesData] == false.
     */
    val dataTypesShared: List<String> = emptyList()
}
