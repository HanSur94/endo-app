// The foreground-coroutine orchestration brain for encrypted export/import (Phase 23-04, APRT-01/02).
// This is where the 23-01 codec, the 23-02 repo snapshot/import, and the 23-03 SAF seam + read-back
// verify compose into two testable flows -- entirely off injected seams so the whole path runs pure-JVM
// (Robolectric only for the android.net.Uri type) with NO real document picker and NO emulator.
//
// SEPARATION INVARIANT (threat T-23-09, CONTEXT "foreground coroutines only"): this file imports NO
// androidx.work / notification / alarm API. Export and import are plain FOREGROUND coroutines -- the
// ~600k-iteration KDF runs off the main thread via withContext(dispatcher) (threat T-23-05b / Pitfall
// 4: never block the UI thread), and a spinner is driven by the Working state. The reminders package
// stays the app's SOLE notification owner; ExportSeparationTest pins this file into that invariant.
//
// NO-LEAK INVARIANT (threat T-23-05): every failure surfaces exactly ONE registered ExportImportCopy
// string via messageForError (import) or ExportImportCopy.READ_BACK_FAILED (export). No exception,
// class name, or file byte ever reaches ExportImportUiState. The passphrase CharArray is zeroed in a
// finally and is never logged.
package com.endoapp.app.export

import android.net.Uri
import com.endoapp.app.content.ExportImportCopy
import com.endoapp.app.content.messageForError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The observable state the export/import screen renders. Deliberately small and PROSE-ONLY: every field
 * is a registered [ExportImportCopy] string, never a raw error. The screen shows a spinner for
 * [Working], a confirmation for [Success], and a calm banner for [Error].
 */
sealed interface ExportImportUiState {

    /** Nothing running -- the screen shows the entry controls. */
    data object Idle : ExportImportUiState

    /** A run is in flight (the off-main KDF is working) -- the screen shows the indeterminate spinner. */
    data class Working(val message: String) : ExportImportUiState

    /** The run finished happily -- the screen shows the confirmation. */
    data class Success(val message: String) : ExportImportUiState

    /** The run failed -- the screen shows the ONE gentle message (never an exception detail). */
    data class Error(val message: String) : ExportImportUiState
}

/**
 * Orchestrates the two encrypted-data flows as off-main foreground coroutines.
 *
 * A plain class (the app uses manual composition, not a DI/ViewModel framework for these seams -- see
 * the RealHealthConnectController precedent), so it stays trivially JVM-testable: every collaborator is
 * an injected function/interface and a [FakeDocumentIo] + fake suspend seams stand in for the real repo
 * and SAF I/O in the Robolectric+coroutines-test lane.
 *
 * ## Export ([runExport])
 * Working -> exportSnapshot() -> encode (KDF, off-main) -> documentIo.write(uri) -> verifyWrite
 * (read-back, off-main) -> Success(EXPORT_SUCCESS) on a verified write, else Error(READ_BACK_FAILED) --
 * never a premature success (threat T-23-07).
 *
 * ## Import ([runImport])
 * Working -> documentIo.read(uri) -> decode (KDF, off-main) -> on Success importEnvelope() ->
 * Success(IMPORT_SUCCESS); on Failure(error) -> Error(messageForError(error)) -- the three failure
 * modes surface their three distinct gentle messages (threat T-23-05).
 *
 * @param exportSnapshot the consent-gated snapshot source (EndoRepository::exportSnapshot).
 * @param importEnvelope the consent-gated, idempotent import target (EndoRepository::importEnvelope).
 * @param documentIo the SAF read/write seam (real SafDocumentIo, or FakeDocumentIo in tests).
 * @param encode the container encoder (ExportCodec::encode) -- the KDF-heavy step, run off-main.
 * @param decode the container decoder (ExportCodec::decode) -- the KDF-heavy step, run off-main.
 * @param verify the read-back-verify-before-success step (::verifyWrite).
 * @param dispatcher the background dispatcher the KDF runs on (default [Dispatchers.Default]); injected
 *   so a test can assert the encode/decode runs off the caller's (main) thread.
 */
class ExportImportViewModel(
    private val exportSnapshot: suspend () -> ExportEnvelope,
    private val importEnvelope: suspend (ExportEnvelope) -> Unit,
    private val documentIo: DocumentIo,
    private val encode: (ExportEnvelope, CharArray) -> ByteArray = ExportCodec::encode,
    private val decode: (ByteArray, CharArray) -> DecodeResult = ExportCodec::decode,
    private val verify: suspend (DocumentIo, Uri, CharArray) -> Boolean = ::verifyWrite,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val _state = MutableStateFlow<ExportImportUiState>(ExportImportUiState.Idle)

    /** The live state the export/import screen collects. */
    val state: StateFlow<ExportImportUiState> = _state.asStateFlow()

    /**
     * Export: snapshot -> encode (off-main KDF) -> write -> read-back verify -> success/honest-failure.
     * ALWAYS zeroes [passphrase] in the finally and never logs it. Any thrown collaborator failure
     * (write IO, snapshot) surfaces the honest [ExportImportCopy.READ_BACK_FAILED] -- never a leaked
     * exception (threat T-23-05).
     */
    suspend fun runExport(uri: Uri, passphrase: CharArray) {
        _state.value = ExportImportUiState.Working(ExportImportCopy.EXPORT_IN_PROGRESS)
        try {
            val envelope = exportSnapshot()
            // The ~600k KDF lives inside encode -- run it OFF the caller's thread (Pitfall 4 / T-23-05b).
            val bytes = withContext(dispatcher) { encode(envelope, passphrase) }
            documentIo.write(uri, bytes)
            // Read-back verify (its decode is also KDF-heavy) -- off-main too.
            val verified = withContext(dispatcher) { verify(documentIo, uri, passphrase) }
            _state.value = if (verified) {
                ExportImportUiState.Success(ExportImportCopy.EXPORT_SUCCESS)
            } else {
                ExportImportUiState.Error(ExportImportCopy.READ_BACK_FAILED)
            }
        } catch (_: Throwable) {
            // Never leak the detail -- a failed write/snapshot is an honest "nothing was saved".
            _state.value = ExportImportUiState.Error(ExportImportCopy.READ_BACK_FAILED)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    /**
     * Import: read -> decode (off-main KDF) -> importEnvelope -> success; each [ExportError] maps to its
     * ONE gentle message. ALWAYS zeroes [passphrase] in the finally and never logs it. A thrown
     * read/import failure surfaces the gentle [ExportImportCopy.NOT_AN_EXPORT] -- never a leaked
     * exception.
     */
    suspend fun runImport(uri: Uri, passphrase: CharArray) {
        _state.value = ExportImportUiState.Working(ExportImportCopy.IMPORT_IN_PROGRESS)
        try {
            val bytes = documentIo.read(uri)
            // decode carries the ~600k KDF at the GCM step -- run it OFF the caller's thread.
            when (val result = withContext(dispatcher) { decode(bytes, passphrase) }) {
                is DecodeResult.Success -> {
                    importEnvelope(result.envelope)
                    _state.value = ExportImportUiState.Success(ExportImportCopy.IMPORT_SUCCESS)
                }
                is DecodeResult.Failure ->
                    _state.value = ExportImportUiState.Error(messageForError(result.error))
            }
        } catch (_: Throwable) {
            // A read/import IO failure is gently reported as "not an Endo export" -- no leaked detail.
            _state.value = ExportImportUiState.Error(ExportImportCopy.NOT_AN_EXPORT)
        } finally {
            passphrase.fill('\u0000')
        }
    }
}
