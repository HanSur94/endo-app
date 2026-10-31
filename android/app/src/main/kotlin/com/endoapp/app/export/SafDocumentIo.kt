package com.endoapp.app.export

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The real [DocumentIo] over the Storage Access Framework (APRT-01, threat T-23-07).
 *
 * ZERO storage permissions: SAF grants per-document access through the `Uri` the user picked at the
 * document picker (`ACTION_CREATE_DOCUMENT` for export, `ACTION_OPEN_DOCUMENT` for import) — no
 * `READ_/WRITE_/MANAGE_EXTERNAL_STORAGE` is declared (`ExportManifestTest` pins that).
 *
 * This is the seam that legitimately imports `android.*` (`ContentResolver` + `Uri`) — it is EXEMPT
 * from the pure-core `ExportPurityTest` scan (which pins only the crypto/codec/mapper files). All the
 * crypto and framing stays in the android-free [ExportCodec].
 *
 * ## The load-bearing `"wt"` mode (Pitfall 2 / T-23-07)
 * [write] opens the output stream in **`"wt"`** (write + TRUNCATE) mode. Without the `t`, SAF opens in
 * plain write mode which does NOT truncate: writing a SHORTER new ciphertext over a LONGER old file
 * leaves the old file's stale trailing bytes appended past the new content — a corrupt, undecryptable
 * file. `"wt"` truncates first, so a shorter export always fully replaces a longer prior one. The
 * read-back verify ([verifyWrite]) is the second, independent guard: it re-decodes what actually
 * landed on disk before any success is claimed.
 *
 * Both operations run on [Dispatchers.IO] (blocking stream I/O off the main thread). A null stream
 * (the provider refused the open) throws [IOException] rather than silently succeeding.
 */
class SafDocumentIo(private val resolver: ContentResolver) : DocumentIo {

    override suspend fun write(uri: Uri, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        // "wt" = write + TRUNCATE: a shorter ciphertext over a longer old file leaves NO stale tail.
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw IOException("SAF openOutputStream(\"wt\") returned null for $uri")
        stream.use { out ->
            out.write(bytes)
            out.flush()
        }
    }

    override suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri)
            ?: throw IOException("SAF openInputStream returned null for $uri")
        stream.use { it.readBytes() }
    }
}
