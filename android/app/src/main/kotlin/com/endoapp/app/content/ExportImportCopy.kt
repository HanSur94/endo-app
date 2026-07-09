// The registered user-facing copy for the Phase-23 encrypted export/import surface — a new
// `AuditableCopy` source in `:app`. Per the LOAD-BEARING RULE (CLAUDE.md / MEMORY
// `endo-copy-audit-substring-gate`), every string here is registered into [CopyAuditRegistry.sources]
// in the SAME change that introduces the export/import screen that renders it, so `CopyAuditTest` scans
// it from birth. An unregistered string silently escapes the substring scans — registration IS the
// enforcement.
//
// SUBSTRING-GATE DISCIPLINE (RESEARCH §"Copy discipline"): the CopyAudit scan is a case-insensitive
// SUBSTRING match against `ClaimsPolicy` (causal / imperative / alarmist / gendered / precision). Every
// candidate below was hand-verified against the forbidden lists — notably the gendered `her` trap
// (dodged: none of these carry `here`/`where`/`recovered`-style hosts of `her`), the imperative `use`
// trap, and the causal `cure`/`treat`/`will` traps. Nothing here reaches for a stack trace or an
// exception detail — the three error messages are the ONLY thing the user ever sees for a failed
// import, mapped via [messageForError].
package com.endoapp.app.content

import com.endoapp.app.export.ExportError

/**
 * The fixed export/import + passphrase + cannot-recover + progress + 3-gentle-error strings rendered by
 * the Phase-23 export/import surface. Exposed as named constants so each composable references a single
 * source, and collected in [auditableStrings] for the `CopyAudit` substring scan.
 *
 * The three ERROR strings ([NOT_AN_EXPORT], [WRONG_PASSPHRASE], [UNSUPPORTED]) map 1:1 to the
 * [ExportError] taxonomy via [messageForError] — each calm, distinct, and never a leaked exception. The
 * cannot-recover warning ([CANNOT_RECOVER]) is present and honest: a lost passphrase means a file that
 * cannot be opened, stated plainly.
 */
object ExportImportCopy : AuditableCopy {

    // --- Section + entry labels ---

    /** Section header for the export/import controls in Settings. */
    const val SECTION_HEADER = "Your data"

    /** Export entry label — opens the passphrase + destination flow. */
    const val EXPORT_LABEL = "Export my data"

    /** Import entry label — opens the file + passphrase flow. */
    const val IMPORT_LABEL = "Import data"

    // --- Passphrase UX ---

    /** Choose-passphrase field label (export). */
    const val CHOOSE_PASSPHRASE = "Choose a passphrase"

    /** Confirm-passphrase field label (export). */
    const val CONFIRM_PASSPHRASE = "Confirm passphrase"

    /** Shown when the choose + confirm passphrase fields disagree. */
    const val PASSPHRASE_MISMATCH = "The two passphrases don't match."

    /**
     * The honest cannot-be-recovered warning (RESEARCH §"Copy discipline": clear + honest). A lost
     * passphrase means an unopenable file — there is no recovery path, so the copy says so plainly.
     */
    const val CANNOT_RECOVER =
        "Your passphrase cannot be recovered. If it is lost, this file cannot be opened — " +
            "please keep it in a safe place."

    // --- In-progress (indeterminate spinner) ---

    /** Export-in-progress spinner label (the off-main KDF is running). */
    const val EXPORT_IN_PROGRESS = "Preparing your backup…"

    /** Import-in-progress spinner label (the off-main KDF is running). */
    const val IMPORT_IN_PROGRESS = "Opening your file…"

    // --- Success ---

    /** Export success (read-back verified). */
    const val EXPORT_SUCCESS = "Your backup is ready."

    /** Import success (envelope upserted). */
    const val IMPORT_SUCCESS = "Your data was imported."

    // --- Failure: read-back verify (export) ---

    /**
     * Read-back-verify failure (export). The write "succeeded" but the bytes that landed did not decode
     * back — the honest "not saved" message, never a premature success (threat T-23-07).
     */
    const val READ_BACK_FAILED = "The file could not be verified. Nothing was saved."

    // --- Failure: the three gentle import error messages (map 1:1 to ExportError) ---

    /** [ExportError.NotAnExport] — wrong magic / truncated frame / unparseable header. */
    const val NOT_AN_EXPORT = "This doesn't look like an Endo export."

    /** [ExportError.WrongPassphrase] — the GCM tag did not verify (calm, no blame). */
    const val WRONG_PASSPHRASE = "That passphrase didn't match."

    /** [ExportError.Unsupported] — a newer container / header / schema version. */
    const val UNSUPPORTED = "This export was made by a newer version of the app."

    override val auditableStrings: List<String> = listOf(
        SECTION_HEADER,
        EXPORT_LABEL,
        IMPORT_LABEL,
        CHOOSE_PASSPHRASE,
        CONFIRM_PASSPHRASE,
        PASSPHRASE_MISMATCH,
        CANNOT_RECOVER,
        EXPORT_IN_PROGRESS,
        IMPORT_IN_PROGRESS,
        EXPORT_SUCCESS,
        IMPORT_SUCCESS,
        READ_BACK_FAILED,
        NOT_AN_EXPORT,
        WRONG_PASSPHRASE,
        UNSUPPORTED,
    )
}

/**
 * The pure taxonomy → message mapper (threat T-23-05). Maps each [ExportError] to exactly ONE registered
 * [ExportImportCopy] string. The ViewModel calls THIS for an import failure — it never surfaces an
 * exception, class name, or file byte. Total over the sealed [ExportError] (the `when` is exhaustive).
 */
fun messageForError(error: ExportError): String = when (error) {
    ExportError.NotAnExport -> ExportImportCopy.NOT_AN_EXPORT
    ExportError.WrongPassphrase -> ExportImportCopy.WRONG_PASSPHRASE
    ExportError.Unsupported -> ExportImportCopy.UNSUPPORTED
}
