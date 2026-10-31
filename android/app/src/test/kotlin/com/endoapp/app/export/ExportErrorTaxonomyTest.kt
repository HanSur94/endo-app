package com.endoapp.app.export

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The ordered failure-taxonomy proof (APRT-02, threat T-23-05). One case per decode branch — the
 * THREE outcomes (NotAnExport / WrongPassphrase / Unsupported) stay distinct, and NO exception ever
 * escapes [ExportCodec.decode] on a malformed file (every case returns a [DecodeResult.Failure], the
 * assertion itself proves no throw).
 */
class ExportErrorTaxonomyTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val passphrase get() = "the real passphrase".toCharArray()

    private fun envelope() = ExportEnvelope(
        exportedAt = "2026-07-09T00:00:00Z",
        dayEntries = listOf(DayEntryDto(id = "d1", day = "2026-07-01", note = null)),
    )

    private fun validContainer() = ExportCodec.encode(envelope(), passphrase)

    private fun headerLenOf(bytes: ByteArray) = ByteBuffer.wrap(bytes, 5, 4).order(ByteOrder.BIG_ENDIAN).int
    private fun ciphertextOf(bytes: ByteArray) = bytes.copyOfRange(9 + headerLenOf(bytes), bytes.size)
    private fun headerOf(bytes: ByteArray): CryptoHeader =
        json.decodeFromString(CryptoHeader.serializer(), String(bytes, 9, headerLenOf(bytes), Charsets.UTF_8))

    /** Re-frame a container with a replacement header JSON, keeping the original ciphertext. */
    private fun reframe(original: ByteArray, headerJson: ByteArray, containerVersion: Int = 1): ByteArray {
        val ciphertext = ciphertextOf(original)
        return ByteArrayOutputStream().use { out ->
            out.write("ENDO".toByteArray(Charsets.US_ASCII))
            out.write(containerVersion)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(headerJson.size).array())
            out.write(headerJson)
            out.write(ciphertext)
            out.toByteArray()
        }
    }

    private fun assertError(expected: ExportError, bytes: ByteArray, msg: String) {
        val result = ExportCodec.decode(bytes, passphrase)
        assertEquals(msg, DecodeResult.Failure(expected), result)
    }

    // --- NotAnExport branches ---

    @Test
    fun badMagic_isNotAnExport() {
        val bytes = validContainer().also { it[0] = 'X'.code.toByte() }
        assertError(ExportError.NotAnExport, bytes, "Wrong leading magic → NotAnExport.")
    }

    @Test
    fun truncatedUnderNineBytes_isNotAnExport() {
        val bytes = validContainer().copyOf(5) // < 9-byte prefix
        assertError(ExportError.NotAnExport, bytes, "A frame shorter than the 9-byte prefix → NotAnExport.")
    }

    @Test
    fun outOfBoundsHeaderLength_isNotAnExport() {
        // Overwrite headerLength with a value > MAX_HEADER_LEN (64 KiB) — reject before allocating.
        val bytes = validContainer()
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1_000_000).array().copyInto(bytes, 5)
        assertError(ExportError.NotAnExport, bytes, "An out-of-bounds headerLength → NotAnExport.")
    }

    @Test
    fun unparseableHeaderJson_isNotAnExport() {
        val bytes = reframe(validContainer(), "{{{ definitely not json".toByteArray(Charsets.UTF_8))
        assertError(ExportError.NotAnExport, bytes, "An unparseable CryptoHeader JSON → NotAnExport.")
    }

    @Test
    fun wrongHeaderMagic_isNotAnExport() {
        val tampered = headerOf(validContainer()).copy(magic = "NOTENDO")
        val bytes = reframe(validContainer(), json.encodeToString(CryptoHeader.serializer(), tampered).toByteArray(Charsets.UTF_8))
        assertError(ExportError.NotAnExport, bytes, "A wrong in-header magic → NotAnExport.")
    }

    // --- Unsupported branches ---

    @Test
    fun newerContainerVersion_isUnsupported() {
        val bytes = validContainer().also { it[4] = 2 }
        assertError(ExportError.Unsupported, bytes, "containerVersion > 1 → Unsupported.")
    }

    @Test
    fun newerFormatVersion_isUnsupported() {
        val tampered = headerOf(validContainer()).copy(formatVersion = 2)
        val bytes = reframe(validContainer(), json.encodeToString(CryptoHeader.serializer(), tampered).toByteArray(Charsets.UTF_8))
        assertError(ExportError.Unsupported, bytes, "header.formatVersion > 1 → Unsupported.")
    }

    @Test
    fun newerSchemaVersion_isUnsupported() {
        // A genuinely-encrypted container whose envelope carries schemaVersion 2 — decrypts + inflates
        // fine (tag authenticates), then the schema check rejects it.
        val bytes = ExportCodec.encode(envelope().copy(schemaVersion = 2), passphrase)
        assertError(ExportError.Unsupported, bytes, "envelope.schemaVersion > 1 → Unsupported.")
    }

    // --- WrongPassphrase branches ---

    @Test
    fun wrongPassphrase_isWrongPassphrase() {
        val result = ExportCodec.decode(validContainer(), "the WRONG passphrase".toCharArray())
        assertEquals(
            "A wrong passphrase → WrongPassphrase (GCM tag fails).",
            DecodeResult.Failure(ExportError.WrongPassphrase),
            result,
        )
    }

    @Test
    fun tamperedCiphertext_isWrongPassphrase() {
        // Flip the last ciphertext byte — the GCM tag no longer verifies, mapping to WrongPassphrase
        // (the calm "that didn't match" message; our own "wt" writes + read-back never self-corrupt).
        val bytes = validContainer()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        assertError(ExportError.WrongPassphrase, bytes, "A tampered ciphertext byte → WrongPassphrase (tag).")
    }
}
