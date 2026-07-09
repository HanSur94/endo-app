package com.endoapp.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure-JVM guard that the IRREVERSIBLE Room v1 schema baseline is exported AND committed.
 *
 * If this file ever goes missing (e.g. `exportSchema` regressed to false, or the commit was dropped),
 * `MigrationTestHelper` loses its v1 baseline forever and future auto-migrations become impossible
 * (RESEARCH Pitfall 3). Room writes the schema under `<schemaLocation>/<dbClassFqn>/<version>.json`,
 * so the committed artifact is `android/app/schemas/com.endoapp.app.data.EndoDatabase/1.json`.
 */
class SchemaExportTest {

    private val expectedTables = setOf(
        "DayEntry", "SymptomReading", "PainPoint", "BleedingEvent", "FactorReading",
        "ImportedSample", "Medication", "TreatmentState", "CachedInsight", "DismissedInsight",
    )

    /** Walk up from the test working dir until an ancestor holds the committed schema JSON. */
    private fun resolveSchema(): File? {
        val relativeCandidates = listOf(
            "schemas/com.endoapp.app.data.EndoDatabase/1.json",
            "app/schemas/com.endoapp.app.data.EndoDatabase/1.json",
        )
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in relativeCandidates) {
                val candidate = File(dir, rel)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun v1SchemaIsExportedCommittedAndCoversAllTenTables() {
        val schema = resolveSchema()
        assertNotNull(
            "Committed Room v1 schema (schemas/com.endoapp.app.data.EndoDatabase/1.json) not found — " +
                "exportSchema must stay true and the file must be committed.",
            schema,
        )

        val root = Json.parseToJsonElement(schema!!.readText()).jsonObject
        val database = root["database"]!!.jsonObject
        assertTrue(
            "Schema version must be 1, was ${database["version"]}.",
            database["version"]!!.jsonPrimitive.content == "1",
        )

        val tables = database["entities"]!!.jsonArray
            .map { it.jsonObject["tableName"]!!.jsonPrimitive.content }
            .toSet()

        val missing = expectedTables - tables
        assertTrue("v1 schema is missing entity tables: $missing", missing.isEmpty())
        assertTrue(
            "v1 schema should declare all 10 entity tables, found ${tables.size}: $tables",
            tables.size >= 10,
        )
    }
}
