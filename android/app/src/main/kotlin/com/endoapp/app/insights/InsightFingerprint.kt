// D-04 / INST-02 — the pure, launch-stable input fingerprint that drives the InsightsCoordinator's
// cache hit/miss decision (the Kotlin mirror of iOS `InsightFingerprint`).
//
// The coordinator recomputes insights only when its INPUTS change. This fingerprint is the canonical,
// launch-stable summary of those inputs: the snapshot COUNT + the LATEST logged `day` (the edit marker)
// + the dismissed-set canonical. An unchanged fingerprint ⇒ cache HIT (load cached, no recompute); a
// changed fingerprint ⇒ cache MISS (recompute + persist replace-all).
//
// PITFALL 5 (load-bearing): the fingerprint MUST NOT use `Set.hashCode()`/`hashCode()` — the JVM
// randomizes some hashCodes per process, so a hash-based fingerprint could differ across launches and
// FORCE a recompute on every cold start (defeating the instant cached-load, D-04). It also MUST include
// the latest-day epoch, not just the count: an edit that changes the most recent logged day flips the
// fingerprint even when the count is unchanged. We build a CANONICAL string from stable, value-derived
// components only.
//
// PURITY (SC1 posture): no Android import, no I/O, no store access, no logging (T-22-01) — a pure
// value-in / string-out function over the already-mapped `List<DaySnapshot>` package value contract.
package com.endoapp.app.insights

import com.endoapp.insights.DaySnapshot

/** The pure, launch-stable input fingerprint (D-04). [make] is a deterministic function of
 *  `(snapshots, dismissed)` only — identical inputs always yield the identical string, across calls and
 *  across launches. */
object InsightFingerprint {

    /**
     * Build the canonical fingerprint string from the coordinator's inputs.
     *
     * Components (per D-04): `count | latestDayEpoch | dismissedCanonical`
     *   - `count`            — the number of day snapshots (a new/removed day flips it).
     *   - `latestDayEpoch`   — the MAX `day.toEpochDay()` (an edit that changes the most recent logged
     *                          day flips it even when the count is unchanged — Pitfall 5). Empty ⇒ `-1`.
     *   - `dismissedCanonical` — the dismissed signalKeys SORTED and comma-joined, so set ORDER never
     *                          affects the fingerprint and a dismiss/un-dismiss always flips it.
     */
    fun make(snapshots: List<DaySnapshot>, dismissed: Set<String>): String {
        // maxOfOrNull is null for an empty list → -1L keeps the empty case well-formed (stable, no crash).
        val latestDayEpoch = snapshots.maxOfOrNull { it.day.toEpochDay() } ?: -1L
        // Canonical (sorted) join — NOT Set.hashCode() (Pitfall 5). Sorting makes the component
        // order-independent so {"a","b"} and {"b","a"} are identical.
        val dismissedSorted = dismissed.sorted().joinToString(",")
        return "${snapshots.size}|$latestDayEpoch|$dismissedSorted"
    }
}
