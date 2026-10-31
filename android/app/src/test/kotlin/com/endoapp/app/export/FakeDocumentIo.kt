package com.endoapp.app.export

import android.net.Uri
import java.io.IOException

/**
 * In-memory [DocumentIo] double (test-only) — backs a `Uri -> ByteArray` map so the whole
 * export→write→read→import path runs pure-JVM (Robolectric for the `Uri` type) with NO real
 * `ContentResolver` and NO document picker.
 *
 * A healthy [write] stores an exact copy of the bytes; [read] returns an exact copy (defensive copies
 * both ways so a caller mutating its buffer can't corrupt the store). Reading an unwritten [uri] throws
 * [IOException] — mirroring [SafDocumentIo]'s null-stream contract.
 *
 * ## The truncate knob (for the read-back-verify test)
 * With [truncateOnWrite] set, [write] drops the last [dropLastBytes] bytes before storing — simulating
 * SAF's stale-tail / short-write quirk that the real code defeats with `"wt"`. This lets
 * `ReadBackVerifyTest` prove that a write which "succeeds" but lands corrupt makes [verifyWrite] return
 * `false` (the honest "did not verify" signal), never a false success.
 */
class FakeDocumentIo(
    var truncateOnWrite: Boolean = false,
    var dropLastBytes: Int = 1,
) : DocumentIo {

    private val store = mutableMapOf<Uri, ByteArray>()

    /** True after at least one [write] to [uri] (whether healthy or truncated). */
    fun exists(uri: Uri): Boolean = store.containsKey(uri)

    override suspend fun write(uri: Uri, bytes: ByteArray) {
        store[uri] = if (truncateOnWrite && bytes.size > dropLastBytes) {
            bytes.copyOf(bytes.size - dropLastBytes)
        } else {
            bytes.copyOf()
        }
    }

    override suspend fun read(uri: Uri): ByteArray {
        val stored = store[uri] ?: throw IOException("FakeDocumentIo: no document at $uri")
        return stored.copyOf()
    }
}
