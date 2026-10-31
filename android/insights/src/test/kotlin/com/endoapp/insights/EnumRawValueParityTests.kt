package com.endoapp.insights

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Cross-platform enum-raw-value drift guard (AENG-05) — no Swift mirror; NOT part of the 157 count.
 *
 * Two independent mechanisms, both required to be RED-capable:
 *
 *  A. **Swift-source scan (assumption-guarded).** Walks up to the monorepo root, regex-extracts the
 *     `case` lists from `EndoInsights/Sources/EndoInsights/SnapshotEnums.swift` + `CyclePhase.swift`,
 *     and asserts each Kotlin enum's `raw` list (declaration order) equals the Swift case list. Because
 *     the Swift enums are `enum X: String` with NO explicit `= "..."`, the Swift case name IS the raw
 *     value — so this catches any divergence between the Kotlin `raw` strings and the Swift source of
 *     truth. Guarded with `assumeTrue` so `:insights` still passes when built outside the monorepo.
 *
 *  B. **Golden JSON (unconditional).** `enum-raw-values.golden.json` was generated ONCE from the Kotlin
 *     enums and committed; this asserts the live enums still equal it every run, on every host. This is
 *     the backstop that turns raw-value drift into a failing build even if the iOS tree is absent.
 */
class EnumRawValueParityTests {

    /** The live Kotlin enums under guard: name → `raw` values in declaration order. */
    private val kotlinEnums: Map<String, List<String>> = linkedMapOf(
        "SymptomType" to SymptomType.entries.map { it.raw },
        "BodyRegion" to BodyRegion.entries.map { it.raw },
        "FlowIntensity" to FlowIntensity.entries.map { it.raw },
        "FoodTag" to FoodTag.entries.map { it.raw },
        "ActivityType" to ActivityType.entries.map { it.raw },
        "ActivityIntensity" to ActivityIntensity.entries.map { it.raw },
        "SleepQuality" to SleepQuality.entries.map { it.raw },
        "CyclePhase" to CyclePhase.entries.map { it.raw },
    )

    // MARK: - Drift A — scan the Swift sources (assumption-guarded)

    /** Walk up from the test working dir until an ancestor holds the given relative path. */
    private fun resolveFromRoot(relative: String): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }

    private val enumDecl = Regex("""enum\s+(\w+)\s*:\s*String""")

    // A Swift enum-case declaration on its own line: `case pelvicPain` (NOT a `case .food(...):` in a
    // switch, which carries more content). SnapshotEnums/CyclePhase declare one bare case per line.
    private val caseDecl = Regex("""^\s*case\s+(\w+)\s*$""")

    /** Parse `name → [caseName...]` (declaration order) for every `enum X: String` in the file. */
    private fun swiftEnumCases(file: File): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        for (line in file.readLines()) {
            val decl = enumDecl.find(line)
            if (decl != null) {
                current = decl.groupValues[1]
                result[current] = mutableListOf()
                continue
            }
            val c = current
            if (c != null) {
                val caseMatch = caseDecl.find(line)
                if (caseMatch != null) result[c]!!.add(caseMatch.groupValues[1])
            }
        }
        return result
    }

    @Test
    fun kotlinRawValuesMatchSwiftSources() {
        val snapshotEnums = resolveFromRoot("EndoInsights/Sources/EndoInsights/SnapshotEnums.swift")
        val cyclePhase = resolveFromRoot("EndoInsights/Sources/EndoInsights/CyclePhase.swift")
        assumeTrue(
            snapshotEnums != null && cyclePhase != null,
            "Could not resolve the Swift EndoInsights sources — skipping cross-platform drift scan (built outside the monorepo)",
        )

        val swift = buildMap {
            putAll(swiftEnumCases(snapshotEnums!!))
            putAll(swiftEnumCases(cyclePhase!!))
        }

        for ((name, kotlinRaw) in kotlinEnums) {
            val swiftCases = swift[name]
            assertEquals(
                kotlinRaw,
                swiftCases,
                "Enum '$name' drifted from its Swift source: Kotlin `raw` list must equal the Swift `case` list " +
                    "(verbatim, in declaration order). This is the raw-value parity contract (AENG-05).",
            )
        }
    }

    // MARK: - Drift B — golden JSON (unconditional)

    private fun loadGolden(): Map<String, List<String>> {
        val stream = requireNotNull(this::class.java.getResourceAsStream("/enum-raw-values.golden.json")) {
            "enum-raw-values.golden.json missing from test resources — the unconditional drift backstop is gone (AENG-05)"
        }
        val text = stream.bufferedReader().use { it.readText() }
        val obj = Json.parseToJsonElement(text) as JsonObject
        return obj.entries
            .filterNot { it.key.startsWith("_") } // '_'-prefixed keys are notes, not enum rows
            .associate { (name, element) ->
                name to (element as JsonArray).map { it.jsonPrimitive.content }
            }
    }

    @Test
    fun liveEnumsMatchCommittedGoldenJson() {
        val golden = loadGolden()
        // Same enum set on both sides (no enum added/removed without regenerating the golden file).
        assertEquals(
            kotlinEnums.keys,
            golden.keys,
            "Golden JSON enum set diverged from the live enums — regenerate enum-raw-values.golden.json (AENG-05)",
        )
        for ((name, kotlinRaw) in kotlinEnums) {
            assertEquals(
                golden[name],
                kotlinRaw,
                "Enum '$name' raw values drifted from the committed golden JSON (drift B, unconditional).",
            )
        }
    }
}
