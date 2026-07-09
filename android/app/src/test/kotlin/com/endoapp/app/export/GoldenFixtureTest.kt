package com.endoapp.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The committed-golden format-drift guard (APRT-03) — pure JVM, decode-ONLY.
 *
 * Loads the committed `endo-export-golden-v1.enc` from the test-resource classpath and decodes it with
 * [GoldenFixture.KNOWN_PASSPHRASE], asserting it yields exactly [GoldenFixture.knownEnvelope]. This
 * test NEVER re-encodes / regenerates — the artifact is a fixed captured file (SecureRandom makes
 * encode non-deterministic; the generate-once producer, [GoldenFixtureGenerator], stays `@Ignore`d).
 *
 * Red-capable: any future change that breaks reading the v1 wire format (a header field rename, a
 * framing tweak, a GZIP/JSON change, an envelope-shape drift) turns this red — the on-disk format
 * cannot silently diverge from what old exports carry.
 */
class GoldenFixtureTest {

    @Test
    fun committedGolden_decodesToKnownEnvelope() {
        val bytes = javaClass.getResourceAsStream(GoldenFixture.GOLDEN_RESOURCE)?.use { it.readBytes() }
        assertNotNull(
            "The committed golden fixture '${GoldenFixture.GOLDEN_RESOURCE}' was not found on the test " +
                "classpath — it must be committed under src/test/resources/golden/ (APRT-03).",
            bytes,
        )

        val result = ExportCodec.decode(bytes!!, GoldenFixture.KNOWN_PASSPHRASE.toCharArray())

        assertTrue(
            "The committed golden must decode to Success with the known passphrase — a Failure means " +
                "the v1 wire format has drifted. Got: $result",
            result is DecodeResult.Success,
        )
        assertEquals(
            "The decoded envelope must equal the KNOWN envelope — the platform-neutral format is pinned.",
            GoldenFixture.knownEnvelope(),
            (result as DecodeResult.Success).envelope,
        )
    }
}
