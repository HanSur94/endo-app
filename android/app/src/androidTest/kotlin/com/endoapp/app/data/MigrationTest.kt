package com.endoapp.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MigrationTestHelper scaffold (emulator / instrumentation).
 *
 * Proves the committed v1 schema is a VALID migration starting point: the helper reads
 * `com.endoapp.app.data.EndoDatabase/1.json` from the androidTest assets (the `schemas/` dir is
 * registered as an androidTest asset source in build.gradle.kts), opens a real SQLite database at
 * version 1, and validates it against the exported schema.
 *
 * No v1→v2 migration exists yet — when the first schema bump lands, add a
 * `runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)` here. Until then this scaffold locks in
 * that the helper + committed schema are wired correctly (threat T-18-01 mitigation, Plan 02 Task 3).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EndoDatabase::class.java,
    )

    @Test
    fun v1SchemaOpensAsAValidMigrationBaseline() {
        // Creating the DB at v1 loads and validates the exported schema; a mismatch throws.
        helper.createDatabase(TEST_DB, 1).close()
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
    }
}
