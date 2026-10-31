package com.endoapp.app.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Golden-SHAPE + round-trip test for the versioned export FORMAT contract (APER-07).
 *
 * Proves the DTOs serialize/deserialize losslessly through kotlinx-serialization AND that the wire
 * shape matches the locked contract: a top-level schema version, ISO-8601 dates, UUID ids, shared
 * enum raws, nullable health values that survive as null, the derived insight cache EXCLUDED, and
 * the `KdfParams` crypto-spec defaults. No encrypted fixture yet — Phase 23 adds the golden
 * encrypted file + read-back decrypt. A structural guard also asserts NO crypto implementation
 * leaked into the contract layer this phase.
 */
class ExportFormatTest {

    private val json = Json { prettyPrint = false }

    private fun sampleEnvelope(): ExportEnvelope = ExportEnvelope(
        schemaVersion = 1,
        exportedAt = "2026-07-09T12:34:56Z",
        dayEntries = listOf(
            DayEntryDto(id = UUID.randomUUID().toString(), day = "2026-07-09", note = "cramps am"),
        ),
        symptomReadings = listOf(
            // severity NULL on purpose — absence ≠ 0 must survive the round trip.
            SymptomReadingDto(
                id = UUID.randomUUID().toString(),
                day = "2026-07-09",
                typeRaw = "cramping",
                severity = null,
                note = null,
            ),
        ),
        painPoints = listOf(
            PainPointDto(id = UUID.randomUUID().toString(), day = "2026-07-09", regionRaw = "lowerAbdomen", severity = 3),
        ),
        bleedingEvents = listOf(
            BleedingEventDto(id = UUID.randomUUID().toString(), day = "2026-07-09", flowRaw = "medium", clots = null),
        ),
        factorReadings = listOf(
            FactorReadingDto(id = UUID.randomUUID().toString(), day = "2026-07-09", kindRaw = "sleep", valueRaw = "good", amount = 7.5, level = null),
        ),
        medications = listOf(
            MedicationDto(id = UUID.randomUUID().toString(), name = "Ibuprofen", createdAt = "2026-07-01T08:00:00Z", isArchived = null),
        ),
        treatmentStates = listOf(
            TreatmentStateDto(id = UUID.randomUUID().toString(), startedAt = "2026-06-15T00:00:00Z", modeRaw = "naturallyCycling"),
        ),
        importedSamples = listOf(
            ImportedSampleDto(
                id = UUID.randomUUID().toString(),
                hcRecordId = null,
                day = "2026-07-09",
                domainRaw = "sleep",
                sleepHours = 6.0,
                importedAt = "2026-07-09T09:00:00Z",
            ),
        ),
        dismissedInsights = listOf(
            DismissedInsightDto(id = UUID.randomUUID().toString(), signalKey = "sleep.short", dismissedAt = "2026-07-08T20:00:00Z"),
        ),
    )

    @Test
    fun envelopeRoundTripsLosslessly() {
        val original = sampleEnvelope()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ExportEnvelope>(encoded)
        assertEquals("Envelope must survive a JSON round trip unchanged.", original, decoded)
    }

    @Test
    fun cryptoHeaderRoundTripsLosslessly() {
        val original = CryptoHeader(
            magic = "ENDOEXPORT",
            formatVersion = 1,
            kdf = KdfParams(saltBase64 = "c2FsdA=="),
            ivBase64 = "aXYxMg==",
        )
        val decoded = json.decodeFromString<CryptoHeader>(json.encodeToString(original))
        assertEquals("CryptoHeader must survive a JSON round trip unchanged.", original, decoded)
    }

    @Test
    fun goldenShape_schemaVersion_isoDates_uuidIds_dayFormat() {
        val decoded = json.decodeFromString<ExportEnvelope>(json.encodeToString(sampleEnvelope()))

        // Top-level format version present.
        assertEquals(1, decoded.schemaVersion)

        // exportedAt + a timestamp field parse as ISO-8601 instants.
        assertNotNull("exportedAt must be an ISO-8601 instant.", Instant.parse(decoded.exportedAt))
        assertNotNull("Medication.createdAt must be an ISO-8601 instant.", Instant.parse(decoded.medications.first().createdAt))
        assertNotNull("ImportedSample.importedAt must be an ISO-8601 instant.", Instant.parse(decoded.importedSamples.first().importedAt))

        // Every entity id is a UUID string.
        val allIds = decoded.dayEntries.map { it.id } +
            decoded.symptomReadings.map { it.id } +
            decoded.painPoints.map { it.id } +
            decoded.bleedingEvents.map { it.id } +
            decoded.factorReadings.map { it.id } +
            decoded.medications.map { it.id } +
            decoded.treatmentStates.map { it.id } +
            decoded.importedSamples.map { it.id } +
            decoded.dismissedInsights.map { it.id }
        assertTrue("Envelope must carry rows.", allIds.isNotEmpty())
        allIds.forEach { id ->
            // Throws IllegalArgumentException if not a valid UUID → test fails.
            assertEquals("id must round-trip as a canonical UUID string.", id, UUID.fromString(id).toString())
        }

        // Day fields are strict yyyy-MM-dd (LocalDate.parse rejects timestamps).
        val allDays = decoded.dayEntries.map { it.day } +
            decoded.symptomReadings.map { it.day } +
            decoded.painPoints.map { it.day } +
            decoded.bleedingEvents.map { it.day } +
            decoded.factorReadings.map { it.day } +
            decoded.importedSamples.map { it.day }
        allDays.forEach { day ->
            assertEquals("day must be strict yyyy-MM-dd.", day, LocalDate.parse(day).toString())
        }
    }

    @Test
    fun nullableHealthValueStaysNullThroughRoundTrip() {
        val decoded = json.decodeFromString<ExportEnvelope>(json.encodeToString(sampleEnvelope()))
        // The intentionally-null severity must remain null (absence ≠ 0).
        assertNull("severity absence must survive as null, never coerced to 0.", decoded.symptomReadings.first().severity)
        assertNull("clots absence must survive as null.", decoded.bleedingEvents.first().clots)
        assertNull("level absence must survive as null.", decoded.factorReadings.first().level)
    }

    @Test
    fun derivedInsightCacheIsExcludedFromEnvelope() {
        val encoded = json.encodeToString(sampleEnvelope()).lowercase()
        // The recomputable per-insight cache row type must have no array/field in the envelope.
        assertFalse(
            "The derived insight cache must not appear in the export envelope (threat T-18-13).",
            encoded.contains("cached"),
        )
    }

    @Test
    fun kdfParamsDefaultsMatchTheCryptoSpec() {
        val defaults = KdfParams(saltBase64 = "c2FsdA==")
        assertEquals("PBKDF2WithHmacSHA256", defaults.alg)
        assertEquals(600_000, defaults.iterations)
        assertEquals(256, defaults.keyBits)
    }

    /**
     * Guard: this phase is CONTRACT ONLY — no encrypt/decrypt/SAF. Assert the export sources contain
     * no crypto-IMPLEMENTATION tokens (Phase 23 owns those). Comments may DESCRIBE the future crypto;
     * this scans for actual API usage, not prose.
     */
    @Test
    fun noCryptoImplementationExistsInTheContractLayer() {
        val implTokens = listOf(
            "import javax.crypto",
            "Cipher.getInstance",
            "SecretKeyFactory",
            "KeyGenerator",
            ".doFinal(",
            "GCMParameterSpec",
            "PBEKeySpec",
        )
        for (name in listOf("ExportEnvelope.kt", "CryptoHeader.kt")) {
            val src = resolveExportSource(name)
            assertNotNull("Export source $name must exist.", src)
            val text = src!!.readText()
            for (token in implTokens) {
                assertFalse(
                    "$name must not contain crypto-implementation token '$token' — crypto is Phase 23.",
                    text.contains(token),
                )
            }
        }
    }

    private fun resolveExportSource(fileName: String): File? {
        val relativeCandidates = listOf(
            "src/main/kotlin/com/endoapp/app/export/$fileName",
            "app/src/main/kotlin/com/endoapp/app/export/$fileName",
            "android/app/src/main/kotlin/com/endoapp/app/export/$fileName",
        )
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in relativeCandidates) {
                val candidate = File(dir, rel)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }
}
