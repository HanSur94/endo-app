package com.endoapp.app.data

import com.endoapp.app.data.daos.DeleteDayDao
import com.endoapp.app.data.entities.ImportedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The signature carry-over from the iOS GDPR lesson: a STRUCTURAL, red-capable meta-test that the
 * delete-day cascade covers EXACTLY the set of [DayScoped] entities — no more, no less.
 *
 * Rather than a hand-maintained list, the required set is derived by reflecting over the actually
 * compiled entity classes: every class in the `entities` package that implements the [DayScoped]
 * marker MUST be covered by [DeleteDayDao.COVERED_DAY_SCOPED_ENTITIES] (and therefore by the
 * `@Transaction` cascade). This makes the guard impossible to fool:
 *
 *  - Drop `ImportedSample` from the cascade coverage → the derived set has an entity the coverage
 *    lacks → RED (this is what forces the inclusion iOS omitted).
 *  - Add a NEW day-scoped entity later (Phase 21+) without cascading it → the derived set gains an
 *    entity → RED. The cascade can never silently fall behind the schema.
 *
 * NOTE on the derivation source: Room's `@Database` annotation is `AnnotationRetention.BINARY`, so
 * its `entities` list is NOT visible via runtime reflection. We therefore enumerate the compiled
 * `entities` package directly off the test classpath — a stronger source than the annotation (it
 * sees every day-scoped entity that exists, not just those a developer remembered to list).
 *
 * Pure JVM JUnit4 — no Room runtime, no Android context, no emulator.
 */
class CascadeCompletenessTest {

    @Test
    fun cascadeCoversExactlyTheDayScopedEntities() {
        val required: Set<Class<*>> = dayScopedEntitiesOnClasspath()
        val covered: Set<Class<*>> = DeleteDayDao.COVERED_DAY_SCOPED_ENTITIES
            .map { it.java }
            .toSet()

        // Sanity: the reflection actually found the entity classes (guards against an empty match
        // silently passing "empty == empty").
        assertTrue(
            "Expected to discover the day-scoped entities on the classpath; found none.",
            required.isNotEmpty(),
        )
        assertEquals(
            "The delete-day cascade must cover EXACTLY the DayScoped entities. " +
                "Missing from cascade: ${required - covered}. " +
                "Covered but not DayScoped: ${covered - required}.",
            required,
            covered,
        )
    }

    @Test
    fun importedSampleIsCovered_closingTheIosDeleteDayOmission() {
        assertTrue(
            "ImportedSample MUST be in the delete-day cascade — the Android port closes the latent " +
                "iOS deleteDay orphan gap and must not copy the omission.",
            DeleteDayDao.COVERED_DAY_SCOPED_ENTITIES.contains(ImportedSample::class),
        )
    }

    /**
     * Reflect over the compiled `entities` package and return every concrete class implementing the
     * [DayScoped] marker. The single canonical source of "what is day-scoped".
     *
     * Walks EVERY entry on `java.class.path` (unioning across them) — both compiled-class directories
     * and jars — rather than trusting `ClassLoader.getResource`. AGP hands the app's main classes to
     * the unit-test JVM as a JAR (only the *test* classes dir is a plain directory entry), and an
     * empty merged-resources dir can shadow a `getResource` first-match; scanning every entry avoids
     * both traps.
     */
    private fun dayScopedEntitiesOnClasspath(): Set<Class<*>> {
        val pkg = "com.endoapp.app.data.entities"
        val loader = Thread.currentThread().contextClassLoader
        val relDir = pkg.replace('.', '/')

        val simpleNames: Set<String> = System.getProperty("java.class.path")
            .split(File.pathSeparatorChar)
            .map { File(it) }
            .flatMap { entry ->
                when {
                    entry.isDirectory -> (File(entry, relDir).listFiles { file ->
                        file.isFile && file.name.endsWith(".class") && !file.name.contains('$')
                    } ?: emptyArray()).map { it.name.removeSuffix(".class") }

                    entry.isFile && entry.name.endsWith(".jar") -> java.util.jar.JarFile(entry).use { jar ->
                        jar.entries().asSequence()
                            .map { it.name }
                            .filter { name ->
                                name.startsWith("$relDir/") &&
                                    name.endsWith(".class") &&
                                    !name.substringAfterLast('/').contains('$') &&
                                    name.count { c -> c == '/' } == relDir.count { c -> c == '/' } + 1
                            }
                            .map { it.substringAfterLast('/').removeSuffix(".class") }
                            .toList()
                    }

                    else -> emptyList()
                }
            }
            .toSet()

        return simpleNames
            .map { Class.forName("$pkg.$it", false, loader) }
            .filter { candidate ->
                DayScoped::class.java.isAssignableFrom(candidate) &&
                    candidate != DayScoped::class.java &&
                    !candidate.isInterface
            }
            .toSet()
    }
}
