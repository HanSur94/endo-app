package com.endoapp.app.export

import android.net.Uri
import java.io.IOException

/**
 * The read-back-verify-before-success step (APRT-01, threat T-23-07) — the honest bridge between
 * "wrote a file" and "wrote a file I confirmed is decryptable".
 *
 * After the export writes ciphertext to [uri] via [io], the caller MUST call [verifyWrite] and only
 * report success when it returns `true`. It re-opens the just-written document ([DocumentIo.read]),
 * decodes it ([ExportCodec.decode]) with the SAME passphrase, and returns `true` ONLY on
 * [DecodeResult.Success]. A write that "succeeded" but landed truncated / with a stale tail / with the
 * wrong bytes fails to decode → `false` → the caller shows an honest failure, never a false success.
 *
 * This helper is NOT part of the pure-core allowlist (a SAF [Uri] appears in its signature, so it is
 * exempt from `ExportPurityTest`) — but the decode it delegates to stays pure. A read failure
 * ([IOException] from the provider) is itself a "did not verify" signal → `false`, not a thrown crash.
 */
suspend fun verifyWrite(io: DocumentIo, uri: Uri, passphrase: CharArray): Boolean {
    val readBack = try {
        io.read(uri)
    } catch (_: IOException) {
        return false
    }
    return ExportCodec.decode(readBack, passphrase) is DecodeResult.Success
}
