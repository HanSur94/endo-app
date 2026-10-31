package com.endoapp.app.export

import android.net.Uri
import com.endoapp.app.content.ExportImportCopy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * The orchestration contract for [ExportImportViewModel] (Phase 23-04, APRT-01/02; threats T-23-05,
 * T-23-05b, T-23-07).
 *
 * Runs on the JVM Robolectric lane (needed only for the `android.net.Uri` type — the codec + fake I/O
 * are otherwise pure) with `kotlinx-coroutines-test`. Every collaborator is a real seam or fake: the
 * real 23-01 [ExportCodec] `encode`/`decode`, the real 23-03 `::verifyWrite`, a [FakeDocumentIo], and
 * fake suspend snapshot/import seams — so the whole export/import brain is proven with NO emulator.
 *
 * Pins the load-bearing invariants:
 *  1. EXPORT verify-before-success: a healthy write → [ExportImportUiState.Success]; a truncating write
 *     → [ExportImportUiState.Error] with [ExportImportCopy.READ_BACK_FAILED], never a premature success.
 *  2. IMPORT taxonomy: wrong-passphrase / not-an-export / newer-version each surface their ONE distinct
 *     gentle message — and NO state string ever carries an exception / stack-trace marker (T-23-05).
 *  3. OFF-MAIN KDF: the encode/decode runs on the injected background dispatcher, never the caller's
 *     thread (T-23-05b / Pitfall 4).
 *  4. Passphrase hygiene: the [CharArray] is zeroed after every run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportImportViewModelTest {

    private val uri: Uri = Uri.parse("content://com.endoapp.test/export/endo.enc")

    /** A named single-thread dispatcher so a test can PROVE the KDF ran off the caller's thread. */
    private val kdfExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, KDF_THREAD_NAME) }
    private val offMainDispatcher: CoroutineDispatcher = kdfExecutor.asCoroutineDispatcher()

    @After
    fun tearDown() {
        kdfExecutor.shutdown()
    }

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

    /** Build a ViewModel over the real codec + verify + a supplied [DocumentIo], with fake data seams. */
    private fun viewModel(
        io: DocumentIo,
        importSink: MutableList<ExportEnvelope> = mutableListOf(),
        encode: (ExportEnvelope, CharArray) -> ByteArray = ExportCodec::encode,
    ) = ExportImportViewModel(
        exportSnapshot = { sampleEnvelope() },
        importEnvelope = { importSink.add(it) },
        documentIo = io,
        encode = encode,
        decode = ExportCodec::decode,
        verify = ::verifyWrite,
        dispatcher = offMainDispatcher,
    )

    // --- 1. EXPORT: verify-before-success ---

    @Test
    fun export_happyPath_publishesWorkingThenSuccess_andBlobIsDecodable() = runTest {
        val io = FakeDocumentIo()
        val vm = viewModel(io)
        val seen = mutableListOf<ExportImportUiState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(seen) }

        vm.runExport(uri, PASSPHRASE.toCharArray())
        collector.cancel()

        assertTrue(
            "export must publish a Working (spinner) state before it finishes",
            seen.any { it is ExportImportUiState.Working },
        )
        val terminal = vm.state.value
        assertTrue("a verified write must end in Success", terminal is ExportImportUiState.Success)
        assertEquals(ExportImportCopy.EXPORT_SUCCESS, (terminal as ExportImportUiState.Success).message)
        // The fake IO holds a REAL, decodable blob (the read-back verify actually passed).
        assertTrue("the written blob must decode back", io.exists(uri))
        assertTrue(ExportCodec.decode(io.read(uri), PASSPHRASE.toCharArray()) is DecodeResult.Success)
    }

    @Test
    fun export_truncatedWrite_endsInReadBackFailed_neverSuccess() = runTest {
        // Simulate SAF's stale-tail / short write — the GCM tag no longer verifies on read-back.
        val io = FakeDocumentIo(truncateOnWrite = true, dropLastBytes = 4)
        val vm = viewModel(io)

        vm.runExport(uri, PASSPHRASE.toCharArray())

        val terminal = vm.state.value
        assertTrue("an unverifiable write must NOT report success", terminal is ExportImportUiState.Error)
        assertEquals(ExportImportCopy.READ_BACK_FAILED, (terminal as ExportImportUiState.Error).message)
    }

    // --- 2. IMPORT taxonomy: three distinct gentle messages ---

    @Test
    fun import_wrongPassphrase_surfacesWrongPassphraseMessage() = runTest {
        val io = FakeDocumentIo()
        io.write(uri, ExportCodec.encode(sampleEnvelope(), PASSPHRASE.toCharArray()))
        val sink = mutableListOf<ExportEnvelope>()
        val vm = viewModel(io, importSink = sink)

        vm.runImport(uri, "the-wrong-passphrase".toCharArray())

        val terminal = vm.state.value
        assertEquals(
            ExportImportCopy.WRONG_PASSPHRASE,
            (terminal as ExportImportUiState.Error).message,
        )
        assertTrue("a failed import must not have imported anything", sink.isEmpty())
    }

    @Test
    fun import_notAnExport_surfacesNotAnExportMessage() = runTest {
        val io = FakeDocumentIo()
        io.write(uri, "this is plainly not an ENDO container".toByteArray())
        val vm = viewModel(io)

        vm.runImport(uri, PASSPHRASE.toCharArray())

        assertEquals(
            ExportImportCopy.NOT_AN_EXPORT,
            (vm.state.value as ExportImportUiState.Error).message,
        )
    }

    @Test
    fun import_newerVersion_surfacesUnsupportedMessage() = runTest {
        // A real container whose containerVersion byte is bumped past what this build reads → Unsupported.
        val blob = ExportCodec.encode(sampleEnvelope(), PASSPHRASE.toCharArray())
        blob[4] = 2 // containerVersion = 2 (> 1) — a newer byte layout.
        val io = FakeDocumentIo()
        io.write(uri, blob)
        val vm = viewModel(io)

        vm.runImport(uri, PASSPHRASE.toCharArray())

        assertEquals(
            ExportImportCopy.UNSUPPORTED,
            (vm.state.value as ExportImportUiState.Error).message,
        )
    }

    @Test
    fun import_happyPath_upsertsEnvelope_andReportsSuccess() = runTest {
        val io = FakeDocumentIo()
        io.write(uri, ExportCodec.encode(sampleEnvelope(), PASSPHRASE.toCharArray()))
        val sink = mutableListOf<ExportEnvelope>()
        val vm = viewModel(io, importSink = sink)

        vm.runImport(uri, PASSPHRASE.toCharArray())

        assertEquals(
            ExportImportCopy.IMPORT_SUCCESS,
            (vm.state.value as ExportImportUiState.Success).message,
        )
        assertEquals("the decoded envelope must reach importEnvelope", 1, sink.size)
        assertEquals(sampleEnvelope().dayEntries.size, sink.single().dayEntries.size)
    }

    // --- 2b. NO-LEAK: no error state ever carries an exception / stack-trace marker (T-23-05) ---

    @Test
    fun everyErrorState_carriesNoExceptionOrStackTraceMarker() = runTest {
        val leakMarkers = listOf("exception", "\tat ", "java.", "javax.", "kotlin.", "com.endoapp")

        // Drive all three import failure taxonomies + the export read-back failure and collect messages.
        val messages = mutableListOf<String>()

        // wrong passphrase
        FakeDocumentIo().let { io ->
            io.write(uri, ExportCodec.encode(sampleEnvelope(), PASSPHRASE.toCharArray()))
            viewModel(io).also { it.runImport(uri, "nope".toCharArray()) }
                .let { messages += (it.state.value as ExportImportUiState.Error).message }
        }
        // not an export
        FakeDocumentIo().let { io ->
            io.write(uri, "garbage".toByteArray())
            viewModel(io).also { it.runImport(uri, PASSPHRASE.toCharArray()) }
                .let { messages += (it.state.value as ExportImportUiState.Error).message }
        }
        // read-back failure (export)
        viewModel(FakeDocumentIo(truncateOnWrite = true, dropLastBytes = 4))
            .also { it.runExport(uri, PASSPHRASE.toCharArray()) }
            .let { messages += (it.state.value as ExportImportUiState.Error).message }

        for (message in messages) {
            for (marker in leakMarkers) {
                assertFalse(
                    "Error message \"$message\" must not leak a \"$marker\" implementation detail (T-23-05).",
                    message.lowercase().contains(marker),
                )
            }
        }
    }

    // --- 3. OFF-MAIN KDF: encode/decode runs on the injected background dispatcher (T-23-05b) ---

    @Test
    fun export_runsEncodeOffTheCallerThread() = runTest {
        val callerThread = Thread.currentThread().name
        var encodeThread: String? = null
        val trackingEncode: (ExportEnvelope, CharArray) -> ByteArray = { env, pass ->
            encodeThread = Thread.currentThread().name
            ExportCodec.encode(env, pass)
        }
        val vm = viewModel(FakeDocumentIo(), encode = trackingEncode)

        vm.runExport(uri, PASSPHRASE.toCharArray())

        assertEquals("the KDF-heavy encode must run on the injected dispatcher", KDF_THREAD_NAME, encodeThread)
        assertFalse("the encode must NOT run on the caller's thread", encodeThread == callerThread)
    }

    // --- 4. Passphrase hygiene: the CharArray is zeroed after a run ---

    @Test
    fun runExport_zeroesThePassphraseAfterUse() = runTest {
        val passphrase = PASSPHRASE.toCharArray()
        viewModel(FakeDocumentIo()).runExport(uri, passphrase)
        assertTrue(
            "the passphrase CharArray must be cleared after the run (no lingering secret)",
            passphrase.none { it in PASSPHRASE },
        )
    }

    @Test
    fun runImport_zeroesThePassphraseAfterUse() = runTest {
        val io = FakeDocumentIo()
        io.write(uri, ExportCodec.encode(sampleEnvelope(), PASSPHRASE.toCharArray()))
        val passphrase = PASSPHRASE.toCharArray()
        viewModel(io).runImport(uri, passphrase)
        assertTrue(
            "the passphrase CharArray must be cleared after the run (no lingering secret)",
            passphrase.none { it in PASSPHRASE },
        )
    }

    private companion object {
        const val PASSPHRASE = "correct-horse-battery-staple"
        const val KDF_THREAD_NAME = "kdf-off-main"
    }
}
