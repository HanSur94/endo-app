package com.endoapp.app.export

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fresh-salt/nonce-per-export proof (APRT-03, threat T-23-01). Encoding the SAME envelope with the
 * SAME passphrase twice must yield two DIFFERENT byte arrays — the header's `saltBase64` and
 * `ivBase64` differ and the ciphertext region differs — while the leading magic+version prefix is
 * identical. Fresh salt → fresh key; fresh IV → fresh nonce; GCM nonce reuse is structurally
 * impossible across exports.
 */
class TwoExportsDifferTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val passphrase get() = "the same passphrase both times".toCharArray()

    private fun envelope() = ExportEnvelope(
        exportedAt = "2026-07-09T00:00:00Z",
        dayEntries = listOf(DayEntryDto(id = "d1", day = "2026-07-01", note = "identical data")),
    )

    private fun parseHeader(bytes: ByteArray): CryptoHeader {
        val headerLen = ByteBuffer.wrap(bytes, 5, 4).order(ByteOrder.BIG_ENDIAN).int
        return json.decodeFromString(CryptoHeader.serializer(), String(bytes, 9, headerLen, Charsets.UTF_8))
    }

    private fun ciphertextRegion(bytes: ByteArray): ByteArray {
        val headerLen = ByteBuffer.wrap(bytes, 5, 4).order(ByteOrder.BIG_ENDIAN).int
        return bytes.copyOfRange(9 + headerLen, bytes.size)
    }

    @Test
    fun twoEncodesOfIdenticalData_differ_butShareTheMagicPrefix() {
        val env = envelope()
        val first = ExportCodec.encode(env, passphrase)
        val second = ExportCodec.encode(env, passphrase)

        assertFalse("Two exports of identical data must differ (fresh salt/IV).", first.contentEquals(second))

        // The first 5 bytes — "ENDO" magic + containerVersion — are identical.
        assertArrayEquals(
            "The magic+version prefix must be identical across exports.",
            first.copyOf(5),
            second.copyOf(5),
        )

        val h1 = parseHeader(first)
        val h2 = parseHeader(second)
        assertNotEquals("Salt must be fresh per export.", h1.kdf.saltBase64, h2.kdf.saltBase64)
        assertNotEquals("IV must be fresh per export.", h1.ivBase64, h2.ivBase64)

        assertFalse(
            "The ciphertext region must differ (fresh key + fresh nonce).",
            ciphertextRegion(first).contentEquals(ciphertextRegion(second)),
        )
    }
}
