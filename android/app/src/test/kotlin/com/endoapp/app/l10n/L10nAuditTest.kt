package com.endoapp.app.l10n

import com.endoapp.app.content.ClaimsPolicy
import com.endoapp.app.content.forLanguage
import com.endoapp.insights.l10n.PolicyLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The DE/FR/ES counterpart of [CopyAuditTest][com.endoapp.app.content.CopyAuditTest] (SAFE-01, SC4).
 *
 * Scans every string the [L10nSourceRegistry] extracts from the REAL repo (`endo.repoRoot`) and asserts
 * each one is clean against its language's policy, `ClaimsPolicy.forLanguage(lang)`.
 *
 * THE LOAD-BEARING RULE: every localized file is registered in [L10nSourceRegistry.sources] IN THE SAME
 * CHANGE that introduces it. An unregistered string silently escapes this scan, so registration IS the
 * enforcement (the tripwire test fails on any localized file no source claims).
 *
 * NON-VACUITY: a scan over too few strings, or over a helper that never fires, would pass trivially. So
 * the real scan asserts a floor on how many strings it saw per language, and the controls below feed a
 * seeded bad sentence per language and a MUTATED COPY of the real `l10n/` folder through the SAME
 * [L10nSourceRegistry.auditFindings] helper. If a control turns red, the real scan would too.
 */
class L10nAuditTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun registeredStrings_passPolicy() {
        val strings = L10nSourceRegistry.collect(L10nSourceRegistry.repoRoot())
        for (lang in PolicyLanguage.entries) {
            assertTrue("The audit must see $lang strings (registered sources are missing).", strings.any { it.lang == lang })
        }
        assertTrue("The audit must scan at least 9 strings (saw ${strings.size}).", strings.size >= 9)
        val findings = L10nSourceRegistry.auditFindings(strings)
        assertTrue(
            "Registered DE/FR/ES strings must carry no forbidden claim:\n" + findings.joinToString("\n"),
            findings.isEmpty(),
        )
    }

    @Test
    fun audit_catchesSeededBadSentences() {
        val seeded = PolicyLanguage.entries.map { lang ->
            L10nString(lang, ClaimsPolicy.forLanguage(lang).selfTest.mustFail.first().text, "seeded:${lang.tag}")
        }
        val findings = L10nSourceRegistry.auditFindings(seeded)
        assertEquals(
            "Each seeded mustFail sentence must yield exactly one finding (harness is enforcing): $findings",
            3,
            findings.size,
        )
    }

    @Test
    fun audit_catchesMutatedRepoCopy() {
        val root = copyRepoL10n()
        val policyFile = File(root, "l10n/claims-policy.de.json")
        rewriteJson(policyFile) { obj ->
            val framing = obj.getValue("framing").jsonArray
            val mutated = JsonArray(
                listOf(JsonPrimitive(framing[0].jsonPrimitive.content + " erkennt Schübe")) + framing.drop(1),
            )
            JsonObject(obj + ("framing" to mutated))
        }
        val findings = L10nSourceRegistry.auditFindings(L10nSourceRegistry.collect(root))
        assertTrue(
            "A forbidden claim planted in the DE framing must be reported against claims-policy.de.json: $findings",
            findings.any { it.contains("claims-policy.de.json") },
        )
    }

    /** A temp root holding a byte copy of the repo `l10n/` folder. */
    private fun copyRepoL10n(): File {
        val root = tmp.newFolder("repo")
        File(L10nSourceRegistry.repoRoot(), "l10n").copyRecursively(File(root, "l10n"))
        return root
    }

    private fun rewriteJson(file: File, transform: (JsonObject) -> JsonElement) {
        val rewritten = transform(Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject)
        file.writeText(rewritten.toString(), Charsets.UTF_8)
    }
}
