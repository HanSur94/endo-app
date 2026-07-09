package com.endoapp.app.export

import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * The committed golden-fixture DEFINITION + its generate-once producer (APRT-03).
 *
 * [GoldenFixture] holds the KNOWN passphrase constant and the KNOWN [ExportEnvelope] that both this
 * generator and [GoldenFixtureTest] reference. The committed `endo-export-golden-v1.enc`, decoded with
 * [GoldenFixture.KNOWN_PASSPHRASE], MUST yield exactly [GoldenFixture.knownEnvelope] — so the
 * platform-neutral v1 wire format cannot silently drift (any future change that breaks reading the v1
 * bytes turns [GoldenFixtureTest] red).
 *
 * ## Why the fixture is CAPTURED, not regenerated
 * [ExportCodec.encode] draws a FRESH 16-byte salt + 12-byte IV from `SecureRandom` on every call, so
 * its output is non-deterministic — the same envelope encodes to different bytes each run. The golden
 * is therefore a ONE-OFF captured artifact: [generateGoldenFixture] was run ONCE by the executor to
 * write the `.enc`, which was then COMMITTED; the generator is left `@Ignore`d so CI never regenerates
 * (and thus never overwrites) it. The self-describing header means the committed file decodes with its
 * OWN recorded salt/IV/iterations forever.
 */
object GoldenFixture {

    /** A PUBLIC test passphrase (no real user data is protected by it — see threat T-23-08b). */
    const val KNOWN_PASSPHRASE = "endo-golden-fixture-v1-known-passphrase"

    /** Classpath location of the committed golden artifact (under `src/test/resources/`). */
    const val GOLDEN_RESOURCE = "/golden/endo-export-golden-v1.enc"

    /** The module-relative path the generator writes the captured artifact to. */
    const val GOLDEN_REL_PATH = "src/test/resources/golden/endo-export-golden-v1.enc"

    /**
     * The KNOWN envelope the golden encodes/decodes to — a fixed, small, DETERMINISTIC set of rows
     * spanning several DTO types, including a null-health-value row (`severity = null`, absence ≠ 0)
     * and a null-note row. `exportedAt` is a fixed constant so the CONTENT is deterministic (only the
     * salt/IV differ per encode — which is exactly why the file is captured, not regenerated).
     */
    fun knownEnvelope(): ExportEnvelope = ExportEnvelope(
        schemaVersion = 1,
        exportedAt = "2026-07-09T12:00:00Z",
        dayEntries = listOf(
            DayEntryDto(id = "golden-day-1", day = "2026-07-01", note = "first golden day"),
            DayEntryDto(id = "golden-day-2", day = "2026-07-02", note = null),
        ),
        symptomReadings = listOf(
            SymptomReadingDto(id = "golden-sym-1", day = "2026-07-01", typeRaw = "cramps", severity = 3),
            // Null-health-value row: severity absent (absence ≠ 0) must survive the golden round trip.
            SymptomReadingDto(id = "golden-sym-2", day = "2026-07-02", typeRaw = "fatigue", severity = null),
        ),
        bleedingEvents = listOf(
            BleedingEventDto(id = "golden-bleed-1", day = "2026-07-01", flowRaw = "medium", clots = null),
        ),
        medications = listOf(
            MedicationDto(
                id = "golden-med-1",
                name = "Ibuprofen",
                note = null,
                createdAt = "2026-06-01T08:00:00Z",
                isArchived = false,
            ),
        ),
    )
}

/**
 * The generate-once producer. `@Ignore`d so it NEVER runs in CI (which would draw a fresh salt/IV and
 * overwrite the committed artifact). Un-ignore + run exactly once to (re)capture the golden, commit the
 * resulting `.enc`, then re-`@Ignore`. Pure JVM — no Android type, no emulator.
 */
class GoldenFixtureGenerator {

    /** Walk up from the test working dir to the module `src/test` so the write lands in the right tree. */
    private fun resolveTestRoot(): File? {
        val candidates = listOf("src/test", "app/src/test", "android/app/src/test")
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in candidates) {
                val candidate = File(dir, rel)
                if (candidate.isDirectory) return dir
            }
            dir = dir.parentFile
        }
        return null
    }

    @Ignore("generate-once, committed artifact; SecureRandom is non-deterministic so CI must NOT regenerate")
    @Test
    fun generateGoldenFixture() {
        val moduleRoot = resolveTestRoot()
            ?: error("Could not resolve the module root (src/test not found) to write the golden fixture.")
        val out = File(moduleRoot, GoldenFixture.GOLDEN_REL_PATH)
        out.parentFile?.mkdirs()

        // Fresh CharArray per call — deriveKey may clear its internal copy.
        val bytes = ExportCodec.encode(
            GoldenFixture.knownEnvelope(),
            GoldenFixture.KNOWN_PASSPHRASE.toCharArray(),
        )
        out.writeBytes(bytes)

        // Sanity: what we just wrote must decode straight back to the known envelope.
        val decoded = ExportCodec.decode(out.readBytes(), GoldenFixture.KNOWN_PASSPHRASE.toCharArray())
        assertTrue(
            "The freshly-generated golden must decode to the known envelope.",
            decoded is DecodeResult.Success && decoded.envelope == GoldenFixture.knownEnvelope(),
        )
        println("Golden fixture written: ${out.absolutePath} (${bytes.size} bytes)")
    }
}
