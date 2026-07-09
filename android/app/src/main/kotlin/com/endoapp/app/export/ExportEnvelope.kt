package com.endoapp.app.export

import kotlinx.serialization.Serializable

/**
 * The versioned, platform-neutral export FORMAT (APER-07) — a DATA CONTRACT only.
 *
 * This phase (18) ships the shape; Phase 23 implements encrypt/decrypt + SAF and consumes these
 * DTOs. There is intentionally NO crypto, no `javax.crypto`, no file I/O here.
 *
 * Design rules (mirrored from the 10-entity Room schema, but standalone — these DTOs import NO Room
 * entity type, so the contract layer stays independent of the persistence layer):
 *  - `schemaVersion` — top-level format version, starts at 1; a reader dispatches on it.
 *  - `exportedAt` — ISO-8601 instant in UTC (e.g. `2026-07-09T12:34:56Z`).
 *  - Per-entity arrays; each row's `id` is the app-generated UUID String (idempotent upsert-by-UUID
 *    on import, Phase 23).
 *  - Enum-backed fields carry the shared RAW strings (`typeRaw`, `regionRaw`, `flowRaw`, `kindRaw`,
 *    `valueRaw`, `domainRaw`, `modeRaw`, …) — the exact vocabulary Room stores and `:insights` reads.
 *  - Day identity serializes as `yyyy-MM-dd` (`day`, from `LocalDate.ofEpochDay`); non-day timestamps
 *    serialize as full ISO-8601 instants (`createdAt`, `startedAt`, `importedAt`, `dismissedAt`).
 *  - Optional health values stay NULLABLE in the DTO — absence ≠ 0 survives the round trip.
 *
 * EXCLUDED: the derived insight cache (the recomputable per-insight cache row) is deliberately NOT
 * exported — it is regenerated from the raw rows on import, so exporting it would only bloat the file
 * and risk leaking a stale category label (threat T-18-13). Keeping it out makes the file minimal and
 * honest.
 */
@Serializable
data class ExportEnvelope(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val dayEntries: List<DayEntryDto> = emptyList(),
    val symptomReadings: List<SymptomReadingDto> = emptyList(),
    val painPoints: List<PainPointDto> = emptyList(),
    val bleedingEvents: List<BleedingEventDto> = emptyList(),
    val factorReadings: List<FactorReadingDto> = emptyList(),
    val medications: List<MedicationDto> = emptyList(),
    val treatmentStates: List<TreatmentStateDto> = emptyList(),
    val importedSamples: List<ImportedSampleDto> = emptyList(),
    val dismissedInsights: List<DismissedInsightDto> = emptyList(),
)

/** Per-day anchor + optional free-text note. `day` = `yyyy-MM-dd`. */
@Serializable
data class DayEntryDto(
    val id: String,
    val day: String,
    val note: String? = null,
)

/** A day-anchored symptom reading. `severity` NULLABLE (absence ≠ 0). */
@Serializable
data class SymptomReadingDto(
    val id: String,
    val day: String,
    val typeRaw: String? = null,
    val severity: Int? = null,
    val note: String? = null,
)

/** A day-anchored body-map pain point. `severity` NULLABLE. */
@Serializable
data class PainPointDto(
    val id: String,
    val day: String,
    val regionRaw: String? = null,
    val severity: Int? = null,
)

/** A day-anchored bleeding observation. `clots` NULLABLE (null ≠ recorded false). */
@Serializable
data class BleedingEventDto(
    val id: String,
    val day: String,
    val flowRaw: String? = null,
    val note: String? = null,
    val clots: Boolean? = null,
)

/** The unified lifestyle-factor row, discriminated by `kindRaw`. Every payload column NULLABLE. */
@Serializable
data class FactorReadingDto(
    val id: String,
    val day: String,
    val kindRaw: String? = null,
    val valueRaw: String? = null,
    val amount: Double? = null,
    val level: Int? = null,
    val note: String? = null,
)

/** A saved medication DEFINITION (not day-scoped). `createdAt` = full ISO-8601 instant. */
@Serializable
data class MedicationDto(
    val id: String,
    val name: String? = null,
    val note: String? = null,
    val createdAt: String,
    val isArchived: Boolean? = null,
)

/** Standing treatment/suppression mode (not day-scoped). `startedAt` = full ISO-8601 instant. */
@Serializable
data class TreatmentStateDto(
    val id: String,
    val startedAt: String,
    val modeRaw: String? = null,
)

/** One imported Health Connect / Apple Health sample. `importedAt` = full ISO-8601 instant. */
@Serializable
data class ImportedSampleDto(
    val id: String,
    val hcRecordId: String? = null,
    val day: String,
    val domainRaw: String? = null,
    val flowRaw: String? = null,
    val sleepHours: Double? = null,
    val sleepQualityRaw: String? = null,
    val activityTypeRaw: String? = null,
    val activityMinutes: Double? = null,
    val ateThatDay: Boolean? = null,
    val importedAt: String,
)

/** A durable dismissal, keyed by a non-identifying category `signalKey`. `dismissedAt` = ISO instant. */
@Serializable
data class DismissedInsightDto(
    val id: String,
    val signalKey: String? = null,
    val dismissedAt: String,
)
