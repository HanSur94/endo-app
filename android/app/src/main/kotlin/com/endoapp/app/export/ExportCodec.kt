package com.endoapp.app.export

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

/**
 * The self-describing ENDO container codec (APRT-01/02/03) — the load-bearing primitive every export
 * writes and every import parses. Pure JVM: imports ONLY `java.*` + `kotlinx.serialization` +
 * [ExportCrypto]; NO `android.*` / `androidx.*` type (uses `java.util.Base64`, NOT
 * `android.util.Base64`, so the codec stays emulator-free — `ExportPurityTest` pins that).
 *
 * ## Container byte layout (Pattern 1, big-endian length prefix)
 * ```
 * 0   4   magic = ASCII "ENDO"            reject a non-export in the first 4 bytes, pre-parse/pre-KDF
 * 4   1   containerVersion = 1            byte-layout version (distinct from header.formatVersion AND
 *                                         envelope.schemaVersion — three independent version knobs)
 * 5   4   headerLength (uint32 BIG-ENDIAN) length of the CryptoHeader JSON; bounds-checked before alloc
 * 9   N   CryptoHeader JSON               {"magic":"ENDOEXPORT","formatVersion":1,"kdf":{…},"ivBase64":…}
 * 9+N .   AES-GCM ciphertext‖128-bit tag  GZIP(envelope JSON) encrypted
 * ```
 * GZIP is UNCONDITIONAL at container-v1 (no header flag): encode always deflates, decode always
 * inflates. The KDF params + salt + IV are READ FROM the parsed header on decode, never constants —
 * an older file decrypts with its own recorded iteration count (self-describing-header invariant).
 *
 * ## Ordered failure taxonomy ([decode]) — the ORDER is the [ExportError] mapping (threat T-23-05)
 * magic → containerVersion → bounded headerLength → header JSON parse → header.magic →
 * header.formatVersion → GCM doFinal → GUNZIP/JSON → envelope.schemaVersion. Only the `doFinal` GCM
 * tag failure maps to [ExportError.WrongPassphrase]; everything pre-decrypt is [ExportError.NotAnExport]
 * or [ExportError.Unsupported]; a post-decrypt (already-authenticated) unreadable body is
 * [ExportError.Unsupported]. `decode` NEVER throws on a malformed file — it returns a [DecodeResult].
 */
object ExportCodec {

    /** ASCII magic identifying an ENDO container; rejects a wrong file in the first 4 bytes. */
    private val MAGIC = "ENDO".toByteArray(Charsets.US_ASCII)

    /** The byte-layout version this build writes and can read. */
    private const val CONTAINER_VERSION = 1

    /** Fixed frame prefix size: 4 magic + 1 version + 4 headerLength. */
    private const val PREFIX_LEN = 9

    /** Upper bound on the CryptoHeader JSON length — reject an untrusted oversized length before alloc (T-23-04). */
    private const val MAX_HEADER_LEN = 65_536

    /** The in-header second magic confirming an endo export after the header parses. */
    private const val HEADER_MAGIC = "ENDOEXPORT"

    /** Highest header.formatVersion / envelope.schemaVersion this build understands. */
    private const val MAX_FORMAT_VERSION = 1
    private const val MAX_SCHEMA_VERSION = 1

    /** Derived AES key size in bits (256). */
    private const val KEY_BITS = 256

    /** One tolerant JSON instance — ignore unknown keys on deserialize (defensive, V5). */
    private val json = Json { ignoreUnknownKeys = true }

    /** No-padding Base64 codec (matches the `NO_WRAP`/no-pad salt/IV encoding). */
    private val base64Encoder = Base64.getEncoder().withoutPadding()
    private val base64Decoder = Base64.getDecoder()

    /**
     * Serialize [envelope] → JSON → GZIP → AES-GCM (key = PBKDF2 over a FRESH 16-byte salt) with a
     * FRESH 12-byte IV, then frame it into the ENDO container. Two encodes of identical data differ:
     * fresh salt → fresh key, fresh IV → fresh nonce (GCM nonce-reuse structurally impossible, T-23-01).
     */
    fun encode(envelope: ExportEnvelope, passphrase: CharArray): ByteArray {
        val salt = ExportCrypto.secureRandomBytes(16)
        val iv = ExportCrypto.secureRandomBytes(12)
        val iterations = KdfParams(saltBase64 = "").iterations // 600_000 default from the committed contract

        val gzipped = ByteArrayOutputStream().use { bos ->
            GZIPOutputStream(bos).use { gz ->
                gz.write(json.encodeToString(ExportEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8))
            }
            bos.toByteArray()
        }

        val key = ExportCrypto.deriveKey(passphrase, salt, iterations, KEY_BITS)
        val ciphertext = ExportCrypto.encrypt(gzipped, key, iv)

        val header = CryptoHeader(
            kdf = KdfParams(iterations = iterations, saltBase64 = base64Encoder.encodeToString(salt)),
            ivBase64 = base64Encoder.encodeToString(iv),
        )
        val headerJson = json.encodeToString(CryptoHeader.serializer(), header).toByteArray(Charsets.UTF_8)

        return ByteArrayOutputStream(PREFIX_LEN + headerJson.size + ciphertext.size).use { out ->
            out.write(MAGIC)
            out.write(CONTAINER_VERSION)
            out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(headerJson.size).array())
            out.write(headerJson)
            out.write(ciphertext)
            out.toByteArray()
        }
    }

    /**
     * Parse + decrypt an ENDO container. The ordered checks ARE the [ExportError] taxonomy (see the
     * class KDoc). Never throws for a malformed file — always returns a [DecodeResult].
     */
    fun decode(bytes: ByteArray, passphrase: CharArray): DecodeResult {
        // 1. magic + minimum frame — pre-parse, pre-KDF.
        if (bytes.size < PREFIX_LEN || !bytes.copyOf(4).contentEquals(MAGIC)) {
            return DecodeResult.Failure(ExportError.NotAnExport)
        }

        // 2. containerVersion — a newer byte layout is unsupported, not corrupt.
        val containerVersion = bytes[4].toInt() and 0xFF
        if (containerVersion > CONTAINER_VERSION) {
            return DecodeResult.Failure(ExportError.Unsupported)
        }

        // 3. bounded headerLength — reject an untrusted length before allocating (T-23-04).
        val headerLen = ByteBuffer.wrap(bytes, 5, 4).order(ByteOrder.BIG_ENDIAN).int
        if (headerLen !in 1..MAX_HEADER_LEN || PREFIX_LEN + headerLen > bytes.size) {
            return DecodeResult.Failure(ExportError.NotAnExport)
        }

        // 4. CryptoHeader JSON parse.
        val header = runCatching {
            json.decodeFromString(CryptoHeader.serializer(), String(bytes, PREFIX_LEN, headerLen, Charsets.UTF_8))
        }.getOrElse { return DecodeResult.Failure(ExportError.NotAnExport) }

        // 5. in-header magic — second confirmation.
        if (header.magic != HEADER_MAGIC) {
            return DecodeResult.Failure(ExportError.NotAnExport)
        }

        // 6. header formatVersion — newer header is unsupported.
        if (header.formatVersion > MAX_FORMAT_VERSION) {
            return DecodeResult.Failure(ExportError.Unsupported)
        }

        // 7. derive key from the header's OWN recorded params (never constants), then GCM decrypt.
        val salt = runCatching { base64Decoder.decode(header.kdf.saltBase64) }
            .getOrElse { return DecodeResult.Failure(ExportError.NotAnExport) }
        val iv = runCatching { base64Decoder.decode(header.ivBase64) }
            .getOrElse { return DecodeResult.Failure(ExportError.NotAnExport) }
        val key = ExportCrypto.deriveKey(passphrase, salt, header.kdf.iterations, header.kdf.keyBits)

        val cipherStart = PREFIX_LEN + headerLen
        val plain = try {
            ExportCrypto.decrypt(bytes, key, iv, cipherStart, bytes.size - cipherStart)
        } catch (_: AEADBadTagException) {
            return DecodeResult.Failure(ExportError.WrongPassphrase)
        } catch (_: BadPaddingException) {
            return DecodeResult.Failure(ExportError.WrongPassphrase)
        }

        // 8. GUNZIP + JSON — the tag already authenticated, so an unreadable body is a newer/unsupported
        //    format, not a wrong passphrase.
        val envelope = runCatching {
            val inflated = GZIPInputStream(plain.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
            json.decodeFromString(ExportEnvelope.serializer(), inflated)
        }.getOrElse { return DecodeResult.Failure(ExportError.Unsupported) }

        // 9. envelope schemaVersion — a newer schema is unsupported.
        if (envelope.schemaVersion > MAX_SCHEMA_VERSION) {
            return DecodeResult.Failure(ExportError.Unsupported)
        }
        return DecodeResult.Success(envelope)
    }
}
