package io.github.denisshakinov.rekords.sqlite

import io.github.denisshakinov.rekords.test.RekordsGeneratedIdMigrationTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/** Runs the shared suite of generated ids through a migration against SQLite, through the bundled driver the JVM has. */
class JvmSQLiteRekordsGeneratedIdMigrationTest : RekordsGeneratedIdMigrationTest(
    SQLite.create { name = temporaryGeneratedIdMigrationDatabaseFile().path }
) {

    @Test
    override fun check_numbers_given_go_with_a_rekord_type_renamed_or_removed() = super.check_numbers_given_go_with_a_rekord_type_renamed_or_removed()
}

private fun temporaryGeneratedIdMigrationDatabaseFile(): File =
    Files.createTempDirectory("rekords").toFile().also { it.deleteOnExit() }.resolve("generated-id-migration.db")
