// EndoApp (:app) — the composition-root persistence seams over the consent-gated [EndoRepository]
// (ATRK-01/02, Plan 20-03). This is the thin row⇄repository glue the running app wires from the
// composition root so the Check-In and Day Log screens ACTUALLY persist through Room — closing the
// deferral Plans 20-01/02 left (the screens took `persistDay`/`loadDay`/`recentDays` as defaulted
// no-op seams). The screens stay DAO-free; only this object (and the composition root) touches the
// repository.
//
// Two write semantics, deliberately distinct:
//   - [upsertDay]  — the Check-In fast-log MERGE (never deletes existing rows; an empty confirm just
//     records the DayEntry marker over whatever the day already holds).
//   - [replaceDay] — the Day Log editor AUTHORITATIVE edit (purge-then-write via the atomic delete
//     cascade), so a field the user DESELECTED is honestly removed, not left behind as a stale row.
//
// The absence ≠ 0 honesty is entirely the pure [DayDraftMapper]'s job; this object only moves the rows.
package com.endoapp.app.ui.tracking

import com.endoapp.app.data.EndoRepository

/** The row⇄repository persistence seams the composition root binds into the Log screens. */
object LogPersistence {

    /**
     * MERGE the day's rows (Check-In fast-log). Upserts the anchor + every touched row; leaves any
     * existing day-scoped row this write does not mention untouched. Never deletes.
     */
    suspend fun upsertDay(repo: EndoRepository, rows: DayLogRows) {
        repo.upsertDayEntry(rows.dayEntry)
        rows.symptoms.forEach { repo.upsertSymptom(it) }
        rows.pain.forEach { repo.upsertPain(it) }
        rows.bleeding.forEach { repo.upsertBleeding(it) }
        rows.factors.forEach { repo.upsertFactor(it) }
    }

    /**
     * REPLACE the whole day (Day Log editor). Purges the day via the atomic delete cascade, then writes
     * the mapper's full desired row set — so a deselected symptom/pain/factor is genuinely gone on the
     * next reload (the honest edit semantic). [rows] carries [epochDay] on its anchor.
     */
    suspend fun replaceDay(repo: EndoRepository, rows: DayLogRows) {
        repo.deleteDay(rows.dayEntry.epochDay)
        upsertDay(repo, rows)
    }

    /**
     * Load a day's [DayDraft], or `null` when the day was never logged (no anchor [DayEntry]) — the
     * signal the Day Log editor uses to distinguish a fresh past day (no delete offered) from an
     * existing one. Preserves nil-vs-0 exactly through [DayDraftMapper.fromRows].
     */
    suspend fun loadDay(repo: EndoRepository, epochDay: Long): DayDraft? {
        val entries = repo.dayEntriesForDay(epochDay)
        if (entries.isEmpty()) return null
        return DayDraftMapper.fromRows(
            DayLogRows(
                dayEntry = entries.first(),
                symptoms = repo.symptomsForDay(epochDay),
                pain = repo.painForDay(epochDay),
                bleeding = repo.bleedingForDay(epochDay),
                factors = repo.factorsForDay(epochDay),
            ),
        )
    }
}
