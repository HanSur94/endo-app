package com.endoapp.app.export

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The pure-JVM crypto core for encrypted export/import (APRT-01, threat T-23-02/03).
 *
 * Zero-dependency by mandate (STACK.md): only `javax.crypto` + `java.security` — NO Tink, NO native
 * Argon2, NO deprecated `androidx.security:security-crypto`. This object imports NO `android.*` /
 * `androidx.*` type so the whole crypto/codec layer runs on the JVM test lane with no emulator
 * (`ExportPurityTest` pins that boundary).
 *
 * Primitives:
 *  - [deriveKey] — `PBKDF2WithHmacSHA256` over a caller-supplied salt/iterations/keyBits. The params
 *    are ALWAYS passed in (never inline literals) so an old file decrypts with its own recorded
 *    header params — the self-describing-header invariant (see [CryptoHeader]/[KdfParams]).
 *  - [encrypt]/[decrypt] — `AES/GCM/NoPadding` with a 128-bit tag; the appended tag IS the
 *    wrong-passphrase / tamper signal ([decrypt] lets `AEADBadTagException` propagate for the codec
 *    to map).
 *  - [secureRandomBytes] — cryptographically-random salt/IV material from a shared [SecureRandom].
 *
 * Hygiene (T-23-03): [deriveKey] zeroes the intermediate raw key bytes after wrapping them into the
 * `SecretKeySpec`, and calls `PBEKeySpec.clearPassword()` in a `finally`. Nothing here logs the
 * passphrase, key bytes, or plaintext, and the file performs no I/O.
 */
object ExportCrypto {

    /** PBKDF2-HMAC-SHA256 — available since API 26 (= minSdk); NOT the SHA-1 default. */
    private const val KDF_ALG = "PBKDF2WithHmacSHA256"

    /** AES-GCM with no padding; the 128-bit tag is appended to / stripped from the ciphertext. */
    private const val CIPHER_TRANSFORM = "AES/GCM/NoPadding"

    /** GCM authentication-tag length in bits. */
    private const val GCM_TAG_BITS = 128

    /** One shared, thread-safe CSPRNG for all salt/IV generation. */
    private val secureRandom = SecureRandom()

    /**
     * Derive a 256-bit (per [keyBits]) AES key from [passphrase] via PBKDF2-HMAC-SHA256.
     *
     * [iterations] and [keyBits] are PARAMETERS, never inline constants — the caller passes the values
     * recorded in the file's header so an older export decrypts with its own params. Deterministic:
     * identical inputs yield identical key bytes; a different salt yields a different key.
     *
     * Hygiene: the raw derived byte array is zeroed after wrapping into the `SecretKeySpec`, and the
     * `PBEKeySpec` copy of the passphrase is cleared in a `finally`.
     */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, keyBits)
        try {
            val factory = SecretKeyFactory.getInstance(KDF_ALG)
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES").also { keyBytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Encrypt [plaintext] under [key] with the given 12-byte [iv] using AES/GCM/NoPadding. The
     * returned array is the ciphertext with the 128-bit tag appended (javax.crypto's `doFinal`
     * contract).
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypt the ciphertext‖tag region under [key] with the given 12-byte [iv]. Optionally operates
     * on a sub-range ([offset], [length]) of [ciphertext] so the codec can pass the whole container
     * buffer without copying. A wrong key (wrong passphrase) or tampered/truncated ciphertext makes
     * `doFinal` throw `AEADBadTagException`; that is LET PROPAGATE — the codec maps it to
     * `WrongPassphrase`.
     */
    fun decrypt(
        ciphertext: ByteArray,
        key: SecretKey,
        iv: ByteArray,
        offset: Int = 0,
        length: Int = ciphertext.size,
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext, offset, length)
    }

    /** [n] cryptographically-random bytes from the shared [SecureRandom] (fresh salt / IV material). */
    fun secureRandomBytes(n: Int): ByteArray = ByteArray(n).also { secureRandom.nextBytes(it) }
}
