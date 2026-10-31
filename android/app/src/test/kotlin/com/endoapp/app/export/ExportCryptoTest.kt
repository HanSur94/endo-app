package com.endoapp.app.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * Pure-JVM proof of the [ExportCrypto] primitives (APRT-01, threat T-23-02/03). No Android type, no
 * Robolectric — `javax.crypto` runs on the plain JVM test lane.
 *
 * NOTE: each PBKDF2 derive at the real 600k iterations costs ~1s, so these tests keep the iteration
 * count LOW where the behaviour under test is independent of the count (determinism, salt-sensitivity,
 * round-trip). The 600k default is exercised end-to-end by the codec tests, not repeated here.
 */
class ExportCryptoTest {

    private val passphrase get() = "correct horse battery staple".toCharArray()
    private val salt = ByteArray(16) { it.toByte() }
    private val iterations = 1_000
    private val keyBits = 256

    @Test
    fun deriveKey_isDeterministic_forIdenticalInputs() {
        val a = ExportCrypto.deriveKey(passphrase, salt, iterations, keyBits)
        val b = ExportCrypto.deriveKey(passphrase, salt, iterations, keyBits)
        assertArrayEquals("Identical inputs must derive identical key bytes.", a.encoded, b.encoded)
        assertEquals("Derived key must be 256 bits = 32 bytes.", 32, a.encoded.size)
    }

    @Test
    fun deriveKey_isSaltSensitive() {
        val a = ExportCrypto.deriveKey(passphrase, ByteArray(16) { 1 }, iterations, keyBits)
        val b = ExportCrypto.deriveKey(passphrase, ByteArray(16) { 2 }, iterations, keyBits)
        assertFalse("A different salt must derive a different key.", a.encoded.contentEquals(b.encoded))
    }

    @Test
    fun encryptThenDecrypt_roundTripsExactPlaintext() {
        val key = ExportCrypto.deriveKey(passphrase, salt, iterations, keyBits)
        val iv = ExportCrypto.secureRandomBytes(12)
        val plaintext = "the quick brown fox — with a unicode ✓ and bytes".toByteArray(Charsets.UTF_8)

        val ciphertext = ExportCrypto.encrypt(plaintext, key, iv)
        val recovered = ExportCrypto.decrypt(ciphertext, key, iv)

        assertArrayEquals("decrypt(encrypt(x)) must equal x exactly.", plaintext, recovered)
        assertFalse("Ciphertext must not equal the plaintext.", ciphertext.contentEquals(plaintext))
    }

    @Test
    fun decrypt_withWrongPassphraseKey_throwsAEADBadTagException() {
        val rightKey = ExportCrypto.deriveKey(passphrase, salt, iterations, keyBits)
        val wrongKey = ExportCrypto.deriveKey("wrong passphrase".toCharArray(), salt, iterations, keyBits)
        val iv = ExportCrypto.secureRandomBytes(12)
        val ciphertext = ExportCrypto.encrypt("secret".toByteArray(Charsets.UTF_8), rightKey, iv)

        assertThrows(
            "A wrong-passphrase key must fail the GCM tag with AEADBadTagException.",
            AEADBadTagException::class.java,
        ) {
            ExportCrypto.decrypt(ciphertext, wrongKey, iv)
        }
    }

    @Test
    fun secureRandomBytes_returnsRequestedLength_andDiffersAcrossCalls() {
        val a = ExportCrypto.secureRandomBytes(16)
        val b = ExportCrypto.secureRandomBytes(16)
        assertEquals("secureRandomBytes(n) must return n bytes.", 16, a.size)
        assertEquals("secureRandomBytes(n) must return n bytes.", 16, b.size)
        assertFalse("Two SecureRandom draws must differ (fresh salt/IV per export).", a.contentEquals(b))
    }
}
