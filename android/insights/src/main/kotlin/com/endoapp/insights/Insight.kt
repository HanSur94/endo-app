// EndoInsights (:insights) — the generic Insight result contract.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/Insight.swift`. Pure-JVM; `Codable` →
// kotlinx-serialization `@Serializable`.
//
// DETERMINISM CONTRACT (load-bearing for the Equatable fixtures): `Insight.id` is DERIVED
// deterministically from `kind` + `window` + a signal key, NEVER a random UUID. The SAME inputs always
// produce the SAME id, so a whole `Insight` (and a `List<Insight>`) stays exactly structurally-equal for
// fixture assertions. The engine MUST construct Insights via `Insight.derived(...)`; the memberwise
// constructor is `internal` so an external caller can never inject a random id.
//
// SERIALIZATION: the synthesized decode reads the STORED derived `id` from the payload — it MUST NOT
// re-derive via `DeterministicUUID` (the forbidden anti-pattern). A round-trip therefore preserves the
// exact stored id (`decoded.id == encoded.id`) and the whole value.
package com.endoapp.insights

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.util.UUID

/** The kind of insight. `trend` ships first; the rest are reserved so the downstream contract is
 *  visible without a later breaking change. `raw` is folded into `DeterministicUUID`. */
@Serializable
enum class InsightKind(val raw: String) {
    TREND("trend"),
    CYCLE_PHASE("cyclePhase"),
    CORRELATION("correlation"),
    RECOMMENDATION("recommendation"),
    FLARE("flare"),
}

/** The rolling window a trend is computed over. Anchored on absolute calendar dates ending at the
 *  injected `today` — NOT cycle phase. `raw` is folded into `DeterministicUUID`. */
@Serializable
enum class TrendWindow(val raw: String) {
    LAST7("last7"),
    LAST30("last30"),
    LAST90("last90"),
    ALL_TIME("allTime"),
}

/** Descriptive confidence in an insight. Int-backed + `Comparable` so callers can order it
 *  (`NONE < LOW < MEDIUM < HIGH`). Kotlin enums compare by ordinal, and the declaration order below
 *  makes ordinal == `value`, so the natural ordering matches the Swift `Int`-backed `Comparable`. */
@Serializable
enum class InsightConfidence(val value: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
}

/** A single supporting data point for an insight (for the chart). Swift `date: Date` → `LocalDate`
 *  (the locked zone-free day identity). */
@Serializable
data class InsightPoint(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val value: Double,
)

/** A generic, structurally-equal insight value. `headline`/`detail` are plain, descriptive, non-causal
 *  copy (the claims posture). */
@Serializable
data class Insight internal constructor(
    /** Deterministically derived from `kind` + `window` + `signalKey` — never random. */
    @Serializable(with = UuidSerializer::class) val id: UUID,
    val kind: InsightKind,
    val headline: String,
    val detail: String,
    val window: TrendWindow,
    val confidence: InsightConfidence,
    val points: List<InsightPoint>,
    /** The stable, value-INDEPENDENT signal this insight is about (e.g. `"symptom.pelvicPain.severity"`).
     *  The dismissal key every downstream honesty/dismissal layer matches on, and the string `id` is
     *  derived from — so it survives recomputation across sessions. */
    val signalKey: String,
    /** The REAL evidence count this insight is based on (the producer self-gated on it). The
     *  `HonestyGate` enforces `sampleSize >= minSample(kind)`. NOT folded into `id` derivation. */
    val sampleSize: Int,
    /** `true` ONLY for the deliberate honest "not enough data yet" sub-threshold signal. The gate's
     *  confidence-floor carve-out keys on THIS typed marker, not a forgeable signalKey suffix. NOT
     *  folded into `id` derivation. */
    val isInsufficiencyNotice: Boolean,
) {
    companion object {
        /** THE engine-facing factory. Derives `id` deterministically from `kind` + `window` + `signalKey`,
         *  so the SAME logical insight always has the SAME id and the value stays fully structurally-equal
         *  for exact-list fixture assertions. */
        fun derived(
            kind: InsightKind,
            window: TrendWindow,
            signalKey: String,
            headline: String,
            detail: String,
            confidence: InsightConfidence,
            points: List<InsightPoint>,
            sampleSize: Int,
            isInsufficiencyNotice: Boolean = false,
        ): Insight = Insight(
            id = DeterministicUUID.make(kind = kind, window = window, signalKey = signalKey),
            kind = kind,
            headline = headline,
            detail = detail,
            window = window,
            confidence = confidence,
            points = points,
            signalKey = signalKey,
            sampleSize = sampleSize,
            isInsufficiencyNotice = isInsufficiencyNotice,
        )
    }
}

/** Stable, dependency-free UUID derivation for `Insight.id`.
 *
 *  A v5-style namespaced derivation: the inputs are folded into a deterministic 128-bit digest via
 *  FNV-1a over two 64-bit lanes, then stamped with the RFC-4122 version (5) and variant bits so the
 *  result is a well-formed UUID. The SAME inputs ALWAYS yield the SAME UUID. This is an identity
 *  derivation (NOT a security primitive), so a non-cryptographic hash is appropriate.
 *
 *  Bit-for-bit parity with Swift: Swift's wrapping `&*`/`&+` map to Kotlin `ULong` (whose `*`/`+` wrap
 *  silently). Do NOT use signed `Long` for the lane arithmetic. */
internal object DeterministicUUID {
    /** A fixed namespace string so EndoInsights ids never collide with unrelated derivations. */
    private const val NAMESPACE = "com.endoapp.endoinsights.insight.v1"

    fun make(kind: InsightKind, window: TrendWindow, signalKey: String): UUID {
        val key = "$NAMESPACE|${kind.raw}|${window.raw}|$signalKey"
        val bytes = key.toByteArray(Charsets.UTF_8)

        // Two FNV-1a 64-bit lanes with distinct offset bases → 16 deterministic bytes (ULong wrapping).
        var lane0: ULong = 0xcbf29ce484222325uL // FNV-1a 64-bit offset basis
        var lane1: ULong = 0x9e3779b97f4a7c15uL // a distinct, well-mixed basis
        val prime: ULong = 0x00000100000001b3uL // FNV 64-bit prime
        for (b in bytes) {
            val byte = (b.toInt() and 0xFF).toULong()
            lane0 = (lane0 xor byte) * prime
            lane1 = (lane1 + byte) * prime
        }

        val out = ByteArray(16)
        for (i in 0 until 8) out[i] = ((lane0 shr (i * 8)) and 0xFFuL).toByte()
        for (i in 0 until 8) out[8 + i] = ((lane1 shr (i * 8)) and 0xFFuL).toByte()

        // Stamp RFC-4122 version 5 (name-based) + variant bits so it is a well-formed UUID.
        out[6] = ((out[6].toInt() and 0x0F) or 0x50).toByte()
        out[8] = ((out[8].toInt() and 0x3F) or 0x80).toByte()

        // Build java.util.UUID preserving Swift's byte order: bytes 0..7 = most-significant (big-endian),
        // bytes 8..15 = least-significant. This matches Swift's `UUID(uuid:)` tuple layout exactly.
        var msb = 0L
        var lsb = 0L
        for (i in 0 until 8) msb = (msb shl 8) or (out[i].toLong() and 0xFF)
        for (i in 8 until 16) lsb = (lsb shl 8) or (out[i].toLong() and 0xFF)
        return UUID(msb, lsb)
    }
}

/** Serializes `java.util.UUID` as its canonical string. Round-trip stable (kotlinx has no built-in). */
internal object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.endoapp.insights.UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

/** Serializes `java.time.LocalDate` as an ISO-8601 string. Round-trip stable (kotlinx has no built-in). */
internal object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.endoapp.insights.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
