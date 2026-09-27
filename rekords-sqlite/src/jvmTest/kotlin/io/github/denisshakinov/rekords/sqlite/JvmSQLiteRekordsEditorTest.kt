package io.github.denisshakinov.rekords.sqlite

import java.io.File
import java.nio.file.Files

/**
 * The bundled driver needs no device, so the suite runs on the host and covers the SQL editor for
 * every target that shares it.
 */
class JvmSQLiteRekordsEditorTest : SQLiteRekordsEditorTest(
    SQLite.create { name = temporaryDatabaseFile().path }
)

private fun temporaryDatabaseFile(): File =
    Files.createTempDirectory("rekords").toFile().also { it.deleteOnExit() }.resolve("test.db")
