package com.endoapp.app.export

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The read-back-verify contract (APRT-01, threat T-23-07): a write is only a success if the bytes that
 * actually landed decode back to a valid envelope.
 *
 * Runs on the JVM Robolectric lane (needed only for the `android.net.Uri` type — the codec + fake I/O
 * are otherwise pure). Encodes a small real envelope through the 23-01 [ExportCodec] to get honest
 * ciphertext, writes it through a [FakeDocumentIo], then asserts:
 *  1. a HEALTHY write → [verifyWrite] returns `true` (the round-trip decodes to Success),
 *  2. a TRUNCATED write (stale-tail / short-write simulation) → [verifyWrite] returns `false` — the
 *     honest "did not verify" signal that blocks a false success on an unverified/corrupt write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadBackVerifyTest {

    private val uri: Uri = Uri.parse("content://com.endoapp.test/export/endo-golden.enc")
    private val passphrase = "read-back-verify-passphrase".toCharArray()

    private fun sampleEnvelope() = ExportEnvelope(
        schemaVersion = 1,
        exportedAt = "2026-07-09T10:00:00Z",
        dayEntries = listOf(
            DayEntryDto(id = "d1", day = "2026-07-01", note = "cramps in the morning"),
            DayEntryDto(id = "d2", day = "2026-07-02", note = null),
        ),
        symptomReadings = listOf(
            SymptomReadingDto(id = "s1", day = "2026-07-01", typeRaw = "cramps", severity = 4),
        ),
    )

    @Test
    fun healthyWrite_verifiesTrue() = runBlocking {
        val io = FakeDocumentIo()
        val bytes = ExportCodec.encode(sampleEnvelope(), passphrase.copyOf())

        io.write(uri, bytes)

        assertTrue(
            "A healthy write must read back + decode to Success (read-back verify passes).",
            verifyWrite(io, uri, passphrase.copyOf()),
        )
    }

    @Test
    fun truncatedWrite_verifiesFalse() = runBlocking {
        // Simulate SAF's stale-tail / short write: drop the last bytes so the GCM tag no longer verifies.
        val io = FakeDocumentIo(truncateOnWrite = true, dropLastBytes = 4)
        val bytes = ExportCodec.encode(sampleEnvelope(), passphrase.copyOf())

        io.write(uri, bytes)

        assertTrue("The fake should have stored SOMETHING (the write 'succeeded').", io.exists(uri))
        assertFalse(
            "A truncated/corrupt write must NOT verify — verifyWrite returns false, blocking a false " +
                "success on an unverified write.",
            verifyWrite(io, uri, passphrase.copyOf()),
        )
    }
}
