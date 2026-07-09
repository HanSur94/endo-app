package com.endoapp.app.export

import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.DismissedInsight
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.app.data.entities.TreatmentState
import java.time.Instant
import java.time.LocalDate

/**
 * The ONE conversion place between the Room [com.endoapp.app.data.entities] world and the versioned,
 * platform-neutral [ExportEnvelope] DTOs (APRT-01/02).
 *
 * PURE JVM by construction: this file imports ONLY the entity + DTO types and `java.time.LocalDate` /
 * `java.time.Instant` — NO `android.*`, NO `androidx.*`, NO Room annotation. [ExportPurityTest] pins
 * that so the whole export/import core stays JVM-testable with no emulator.
 *
 * ## Unit conversions — the single place they happen
 *  - Day identity: `entity.epochDay: Long` ⇄ `dto.day: String` (yyyy-MM-dd) via
 *    [LocalDate.ofEpochDay] / [LocalDate.parse].
 *  - Timestamps: `entity.*Millis: Long` ⇄ `dto.*: String` (ISO-8601 instant) via
 *    [Instant.ofEpochMilli] `.toString()` / [Instant.parse] `.toEpochMilli()` —
 *    createdAt (Medication), startedAt (TreatmentState), importedAt (ImportedSample),
 *    dismissedAt (DismissedInsight).
 *
 * ## Absence ≠ 0
 * Every nullable health value (severity, clots, amount, level, sleepHours, …) is carried through the
 * round-trip UNCHANGED — `null` stays `null`, never coerced to 0/false. Non-null is spent only on the
 * identity + timestamp columns the entities themselves make non-null.
 *
 * Export read is `entity.toDto()`; import write is `dto.toEntity()`. Every round-trip
 * `entity.toDto().toEntity()` is identity for all 9 exported types (`CachedInsight` is EXCLUDED — a
 * derived cache the coordinator regenerates on next recompute, per the [ExportEnvelope] contract).
 */
object ExportMapper {

    // --- DayEntry ⇄ DayEntryDto (epochDay ⇄ day) ---

    fun DayEntry.toDto(): DayEntryDto = DayEntryDto(
        id = id,
        day = epochDayToIso(epochDay),
        note = note,
    )

    fun DayEntryDto.toEntity(): DayEntry = DayEntry(
        id = id,
        epochDay = isoToEpochDay(day),
        note = note,
    )

    // --- SymptomReading ⇄ SymptomReadingDto (epochDay ⇄ day; severity nullable) ---

    fun SymptomReading.toDto(): SymptomReadingDto = SymptomReadingDto(
        id = id,
        day = epochDayToIso(epochDay),
        typeRaw = typeRaw,
        severity = severity,
        note = note,
    )

    fun SymptomReadingDto.toEntity(): SymptomReading = SymptomReading(
        id = id,
        epochDay = isoToEpochDay(day),
        typeRaw = typeRaw,
        severity = severity,
        note = note,
    )

    // --- PainPoint ⇄ PainPointDto (epochDay ⇄ day; severity nullable) ---

    fun PainPoint.toDto(): PainPointDto = PainPointDto(
        id = id,
        day = epochDayToIso(epochDay),
        regionRaw = regionRaw,
        severity = severity,
    )

    fun PainPointDto.toEntity(): PainPoint = PainPoint(
        id = id,
        epochDay = isoToEpochDay(day),
        regionRaw = regionRaw,
        severity = severity,
    )

    // --- BleedingEvent ⇄ BleedingEventDto (epochDay ⇄ day; clots nullable) ---

    fun BleedingEvent.toDto(): BleedingEventDto = BleedingEventDto(
        id = id,
        day = epochDayToIso(epochDay),
        flowRaw = flowRaw,
        note = note,
        clots = clots,
    )

    fun BleedingEventDto.toEntity(): BleedingEvent = BleedingEvent(
        id = id,
        epochDay = isoToEpochDay(day),
        flowRaw = flowRaw,
        note = note,
        clots = clots,
    )

    // --- FactorReading ⇄ FactorReadingDto (epochDay ⇄ day; all payload cols nullable) ---

    fun FactorReading.toDto(): FactorReadingDto = FactorReadingDto(
        id = id,
        day = epochDayToIso(epochDay),
        kindRaw = kindRaw,
        valueRaw = valueRaw,
        amount = amount,
        level = level,
        note = note,
    )

    fun FactorReadingDto.toEntity(): FactorReading = FactorReading(
        id = id,
        epochDay = isoToEpochDay(day),
        kindRaw = kindRaw,
        valueRaw = valueRaw,
        amount = amount,
        level = level,
        note = note,
    )

    // --- Medication ⇄ MedicationDto (createdAtMillis ⇄ createdAt ISO; NOT day-scoped) ---

    fun Medication.toDto(): MedicationDto = MedicationDto(
        id = id,
        name = name,
        note = note,
        createdAt = millisToIso(createdAtMillis),
        isArchived = isArchived,
    )

    fun MedicationDto.toEntity(): Medication = Medication(
        id = id,
        name = name,
        note = note,
        createdAtMillis = isoToMillis(createdAt),
        isArchived = isArchived,
    )

    // --- TreatmentState ⇄ TreatmentStateDto (startedAtMillis ⇄ startedAt ISO; NOT day-scoped) ---

    fun TreatmentState.toDto(): TreatmentStateDto = TreatmentStateDto(
        id = id,
        startedAt = millisToIso(startedAtMillis),
        modeRaw = modeRaw,
    )

    fun TreatmentStateDto.toEntity(): TreatmentState = TreatmentState(
        id = id,
        startedAtMillis = isoToMillis(startedAt),
        modeRaw = modeRaw,
    )

    // --- ImportedSample ⇄ ImportedSampleDto (epochDay ⇄ day; importedAtMillis ⇄ importedAt ISO) ---

    fun ImportedSample.toDto(): ImportedSampleDto = ImportedSampleDto(
        id = id,
        hcRecordId = hcRecordId,
        day = epochDayToIso(epochDay),
        domainRaw = domainRaw,
        flowRaw = flowRaw,
        sleepHours = sleepHours,
        sleepQualityRaw = sleepQualityRaw,
        activityTypeRaw = activityTypeRaw,
        activityMinutes = activityMinutes,
        ateThatDay = ateThatDay,
        importedAt = millisToIso(importedAtMillis),
    )

    fun ImportedSampleDto.toEntity(): ImportedSample = ImportedSample(
        id = id,
        hcRecordId = hcRecordId,
        epochDay = isoToEpochDay(day),
        domainRaw = domainRaw,
        flowRaw = flowRaw,
        sleepHours = sleepHours,
        sleepQualityRaw = sleepQualityRaw,
        activityTypeRaw = activityTypeRaw,
        activityMinutes = activityMinutes,
        ateThatDay = ateThatDay,
        importedAtMillis = isoToMillis(importedAt),
    )

    // --- DismissedInsight ⇄ DismissedInsightDto (dismissedAtMillis ⇄ dismissedAt ISO) ---

    fun DismissedInsight.toDto(): DismissedInsightDto = DismissedInsightDto(
        id = id,
        signalKey = signalKey,
        dismissedAt = millisToIso(dismissedAtMillis),
    )

    fun DismissedInsightDto.toEntity(): DismissedInsight = DismissedInsight(
        id = id,
        signalKey = signalKey,
        dismissedAtMillis = isoToMillis(dismissedAt),
    )

    // --- Aggregate helpers (the export SOURCE / import TARGET the repository drives) ---

    /**
     * Build the platform-neutral [ExportEnvelope] from the 9 exported entity lists. `exportedAt` is an
     * ISO-8601 instant the caller supplies (so the repository owns the clock, not this pure mapper).
     */
    fun toEnvelope(
        dayEntries: List<DayEntry>,
        symptomReadings: List<SymptomReading>,
        painPoints: List<PainPoint>,
        bleedingEvents: List<BleedingEvent>,
        factorReadings: List<FactorReading>,
        medications: List<Medication>,
        treatmentStates: List<TreatmentState>,
        importedSamples: List<ImportedSample>,
        dismissedInsights: List<DismissedInsight>,
        exportedAt: String,
    ): ExportEnvelope = ExportEnvelope(
        schemaVersion = 1,
        exportedAt = exportedAt,
        dayEntries = dayEntries.map { it.toDto() },
        symptomReadings = symptomReadings.map { it.toDto() },
        painPoints = painPoints.map { it.toDto() },
        bleedingEvents = bleedingEvents.map { it.toDto() },
        factorReadings = factorReadings.map { it.toDto() },
        medications = medications.map { it.toDto() },
        treatmentStates = treatmentStates.map { it.toDto() },
        importedSamples = importedSamples.map { it.toDto() },
        dismissedInsights = dismissedInsights.map { it.toDto() },
    )

    /** Map every array of an [ExportEnvelope] back to its entity list (the import TARGET). */
    fun toEntities(envelope: ExportEnvelope): EntityBundle = EntityBundle(
        dayEntries = envelope.dayEntries.map { it.toEntity() },
        symptomReadings = envelope.symptomReadings.map { it.toEntity() },
        painPoints = envelope.painPoints.map { it.toEntity() },
        bleedingEvents = envelope.bleedingEvents.map { it.toEntity() },
        factorReadings = envelope.factorReadings.map { it.toEntity() },
        medications = envelope.medications.map { it.toEntity() },
        treatmentStates = envelope.treatmentStates.map { it.toEntity() },
        importedSamples = envelope.importedSamples.map { it.toEntity() },
        dismissedInsights = envelope.dismissedInsights.map { it.toEntity() },
    )

    // --- Unit conversions (the ONLY place these units convert) ---

    private fun epochDayToIso(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    private fun isoToEpochDay(day: String): Long = LocalDate.parse(day).toEpochDay()

    private fun millisToIso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    private fun isoToMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
}

/**
 * The 9 entity lists an [ExportEnvelope] maps back to — the import TARGET the repository hands to the
 * single-`@Transaction` bulk upsert. `CachedInsight` is deliberately absent (excluded from export).
 */
data class EntityBundle(
    val dayEntries: List<DayEntry>,
    val symptomReadings: List<SymptomReading>,
    val painPoints: List<PainPoint>,
    val bleedingEvents: List<BleedingEvent>,
    val factorReadings: List<FactorReading>,
    val medications: List<Medication>,
    val treatmentStates: List<TreatmentState>,
    val importedSamples: List<ImportedSample>,
    val dismissedInsights: List<DismissedInsight>,
)
