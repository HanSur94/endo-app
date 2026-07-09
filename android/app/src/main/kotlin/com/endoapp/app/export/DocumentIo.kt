package com.endoapp.app.export

import android.net.Uri

/**
 * The thin SAF seam (APRT-01) — the ONLY Android file I/O the encrypted export/import touches.
 *
 * The crypto + container codec (23-01, [ExportCodec]) stays a pure `ByteArray` function; this
 * interface is where the Storage Access Framework `Uri` (a user-picked destination — zero storage
 * permissions) enters and leaves. Injecting it lets the whole export→write→read→import path run
 * pure-JVM behind a [FakeDocumentIo] double, with NO real document picker and NO emulator.
 *
 * Two operations only:
 *  - [write] — persist [bytes] to the document at [uri] (truncating any longer prior content).
 *  - [read]  — return the exact bytes of the document at [uri] (for the read-back verify, [verifyWrite]).
 *
 * The real implementation is [SafDocumentIo] (`ContentResolver` + `openOutputStream(uri, "wt")`).
 */
interface DocumentIo {
    /** Write [bytes] to the document at [uri], replacing any existing content (truncate). */
    suspend fun write(uri: Uri, bytes: ByteArray)

    /** Read and return the exact bytes of the document at [uri]. */
    suspend fun read(uri: Uri): ByteArray
}
