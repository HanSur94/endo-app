package com.endoapp.app.export

/**
 * The typed decode-failure taxonomy (APRT-02, threat T-23-05) — three DISTINCT outcomes so import can
 * tell a wrong passphrase from a corrupt file from a too-new file, and show a calm, specific message
 * for each. This file is the taxonomy ONLY: no copy strings live here — the ViewModel (Phase 23-04)
 * maps each [ExportError] to a CopyAudit-registered string. Never surface a stack trace or exception
 * class name to the user.
 *
 * The ORDER in which [ExportCodec.decode] detects these IS the taxonomy (see that function): magic /
 * bounds / header-parse / header-magic checks (→ [NotAnExport]) and version checks (→ [Unsupported])
 * all run BEFORE decryption; only the GCM tag failure at `doFinal` maps to [WrongPassphrase]; a
 * post-decrypt (already-authenticated) body that will not inflate/parse is [Unsupported].
 */
sealed interface ExportError {
    /** Not an Endo export at all: wrong magic, truncated/out-of-bounds frame, or unparseable header. */
    data object NotAnExport : ExportError

    /** The GCM tag did not verify — a wrong passphrase (or a tampered/truncated ciphertext). */
    data object WrongPassphrase : ExportError

    /** A newer container / header / schema version this build cannot read. */
    data object Unsupported : ExportError
}

/**
 * The result of [ExportCodec.decode]: either the recovered [ExportEnvelope] or a typed [ExportError].
 * `decode` NEVER throws for a malformed file — every failure path returns a [Failure].
 */
sealed interface DecodeResult {
    data class Success(val envelope: ExportEnvelope) : DecodeResult
    data class Failure(val error: ExportError) : DecodeResult
}
