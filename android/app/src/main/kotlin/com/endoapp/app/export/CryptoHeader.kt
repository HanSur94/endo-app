package com.endoapp.app.export

import kotlinx.serialization.Serializable

/**
 * The cleartext crypto header prepended to an encrypted export (APER-07, threat T-18-03b) —
 * a DATA CONTRACT ONLY. This phase (18) defines the shape + the mandated defaults; Phase 23
 * implements the actual encrypt/decrypt with `javax.crypto`. There is NO cipher, no key
 * derivation, no `SecureRandom`, no file I/O in this file.
 *
 * On-disk layout (Phase 23): cleartext [CryptoHeader] JSON, then the ciphertext of the
 * [ExportEnvelope] JSON under `AES/GCM/NoPadding` with a 128-bit auth tag. The header is cleartext
 * BY DESIGN — a reader needs the KDF params + IV to derive the key and decrypt, but the params are
 * not secret; the passphrase is (and is never stored). Because the header carries the KDF params,
 * `iterations` can rise over time without breaking older files.
 *
 * INVARIANTS Phase 23 must honour (encoded here as the contract):
 *  - Params are READ FROM THE FILE on import, never hard-coded constants — an old file decrypts with
 *    its own recorded `iterations`/`saltBase64`.
 *  - A FRESH random 16-byte salt AND a fresh 12-byte IV per export (fresh salt → fresh key → the
 *    GCM nonce-reuse catastrophe cannot occur across exports).
 *  - `magic` identifies an endo export so the ciphertext is not mistaken for greppable JSON.
 */
@Serializable
data class CryptoHeader(
    /** Fixed magic string identifying an endo encrypted export. */
    val magic: String = "ENDOEXPORT",
    /** Header/crypto version, independent of [ExportEnvelope.schemaVersion]. */
    val formatVersion: Int = 1,
    /** Key-derivation params, read from the file on import. */
    val kdf: KdfParams,
    /** Base64 of the fresh 12-byte AES-GCM IV for this export. */
    val ivBase64: String,
)

/**
 * PBKDF2 key-derivation parameters (contract only).
 *
 * Defaults follow OWASP-current guidance: `PBKDF2WithHmacSHA256` (available since API 26 = minSdk),
 * ≥600k iterations, a 256-bit derived key, over a fresh per-export 16-byte salt. Phase 23 reads
 * these values from the header rather than assuming them, so the iteration count is upgradeable.
 */
@Serializable
data class KdfParams(
    val alg: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 600_000,
    /** Base64 of the fresh 16-byte salt for this export (Phase 23 fills it from SecureRandom). */
    val saltBase64: String,
    val keyBits: Int = 256,
)
