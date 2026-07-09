package com.endoapp.app.export

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.GZIPOutputStream

/**
 * Pure-JVM round-trip proof of [ExportCodec] (APRT-01/02). Encode → decode recovers the EXACT
 * envelope; the KDF params are read FROM the parsed header, not from a constant (a container whose
 * header records a non-default iteration count still decodes — if decode hard-coded 600k it would
 * derive a different key and fail the GCM tag).
 */
class ExportCodecTest {

    private val passphrase get() = "a fairly long passphrase for the export".toCharArray()

    private fun sampleEnvelope() = ExportEnvelope(
        schemaVersion = 1,
        exportedAt = "2026-07-09T12:34:56Z",
        dayEntries = listOf(
            DayEntryDto(id = "day-1", day = "2026-07-01", note = "a note"),
            DayEntryDto(id = "day-2", day = "2026-07-02", note = null),
        ),
        symptomReadings = listOf(
            SymptomReadingDto(id = "sym-1", day = "2026-07-01", typeRaw = "cramps", severity = 3, note = null),
            SymptomReadingDto(id = "sym-2", day = "2026-07-02", typeRaw = "fatigue", severity = null, note = "tired"),
        ),
        medications = listOf(
            MedicationDto(id = "med-1", name = "ibuprofen", note = null, createdAt = "2026-06-01T08:00:00Z", isArchived = false),
        ),
        bleedingEvents = listOf(
            BleedingEventDto(id = "bl-1", day = "2026-07-01", flowRaw = "medium", note = null, clots = null),
        ),
    )

    @Test
    fun encodeThenDecode_recoversTheExactEnvelope() {
        val original = sampleEnvelope()
        val bytes = ExportCodec.encode(original, passphrase)

        val result = ExportCodec.decode(bytes, passphrase)

        assertTrue("A valid file must decode to Success.", result is DecodeResult.Success)
        assertEquals(
            "Round-trip must recover the exact envelope.",
            original,
            (result as DecodeResult.Success).envelope,
        )
    }

    @Test
    fun decode_readsIterationsFromHeader_notAConstant() {
        // Build a container whose header records a NON-default (1000, not 600k) iteration count.
        val nonDefaultIterations = 1_000
        val bytes = buildContainer(sampleEnvelope(), passphrase, nonDefaultIterations)

        // If decode used a hard-coded 600k it would derive the wrong key → WrongPassphrase. Success
        // proves it read the header's recorded iteration count.
        val result = ExportCodec.decode(bytes, passphrase)
        assertTrue(
            "A container with a non-default iteration count must still decode (params read from header).",
            result is DecodeResult.Success,
        )
        assertEquals(sampleEnvelope(), (result as DecodeResult.Success).envelope)
    }

    /**
     * Test-only re-implementation of the encode framing that lets the caller pin the iteration count,
     * so we can prove decode reads it from the header. Mirrors [ExportCodec.encode]'s layout exactly.
     */
    private fun buildContainer(envelope: ExportEnvelope, passphrase: CharArray, iterations: Int): ByteArray {
        val json = Json { ignoreUnknownKeys = true }
        val enc = Base64.getEncoder().withoutPadding()
        val salt = ExportCrypto.secureRandomBytes(16)
        val iv = ExportCrypto.secureRandomBytes(12)

        val gzipped = ByteArrayOutputStream().use { bos ->
            GZIPOutputStream(bos).use { it.write(json.encodeToString(ExportEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)) }
            bos.toByteArray()
        }
        val key = ExportCrypto.deriveKey(passphrase, salt, iterations, 256)
        val ciphertext = ExportCrypto.encrypt(gzipped, key, iv)
        val header = CryptoHeader(
            kdf = KdfParams(iterations = iterations, saltBase64 = enc.encodeToString(salt)),
            ivBase64 = enc.encodeToString(iv),
        )
        val headerJson = json.encodeToString(CryptoHeader.serializer(), header).toByteArray(Charsets.UTF_8)

        return ByteArrayOutputStream().use { out ->
            out.write("ENDO".toByteArray(Charsets.US_ASCII))
            out.write(1)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(headerJson.size).array())
            out.write(headerJson)
            out.write(ciphertext)
            out.toByteArray()
        }
    }
}
