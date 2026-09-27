@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sqlite

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.addField
import io.github.denisshakinov.rekords.core.removeField
import io.github.denisshakinov.rekords.test.TestGamePropertyRekord
import io.github.denisshakinov.rekords.test.TestGameStateRekord
import io.github.denisshakinov.rekords.test.TestRekordsSchema
import io.github.denisshakinov.rekords.test.TestUpgradingSchema
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The indexes a migration leaves a table with. Nothing a query returns tells whether an index is
 * there, so the database is asked directly, through a connection of its own - which only the JVM,
 * where the database is a file the test chose, lets a test open.
 */
class JvmSQLiteMigrationIndexTest {

    private val databaseFile: File =
        Files.createTempDirectory("rekords").toFile().also { it.deleteOnExit() }.resolve("test.db")

    private val rekordsEditor: RekordsEditor = SQLite.create { name = databaseFile.path }

    /**
     * A table rebuilt by a migration has the indexes its searchable fields had before: whether the
     * field renamed is another one or the searchable one itself.
     */
    @Test
    fun table_rebuilt_by_a_migration_keeps_its_indexes() = runTest {
        RekordsStore(TestRekordsSchema(), rekordsEditor).count<TestGamePropertyRekord>().getOrThrow()
        assertEquals(expected = listOf(TITLE_INDEX), actual = indexes(PROPERTY_TABLE))

        // One field beside the searchable title renamed and back, and the title itself: the table
        // is rebuilt four times.
        val renamingSchema = TestUpgradingSchema(version = 2) {
            for (field in listOf(TestGamePropertyRekord.LOCALE, TestGamePropertyRekord.TITLE)) {
                renameField(PROPERTY_TABLE, field, "${field}_before")
                renameField(PROPERTY_TABLE, "${field}_before", field)
            }
        }
        RekordsStore(renamingSchema, rekordsEditor).count<TestGamePropertyRekord>().getOrThrow()
        assertEquals(expected = listOf(TITLE_INDEX), actual = indexes(PROPERTY_TABLE))
    }

    /** A searchable field a migration adds is indexed the way one the table was created with is. */
    @Test
    fun searchable_field_added_by_a_migration_is_indexed() = runTest {
        RekordsStore(TestRekordsSchema(), rekordsEditor).count<TestGameStateRekord>().getOrThrow()
        assertEquals(expected = listOf(NAME_INDEX), actual = indexes(STATE_TABLE))

        // Removed, which leaves the table without the index, and added again, which has to build it.
        val readdingSchema = TestUpgradingSchema(version = 2) {
            removeField<TestGameStateRekord>(TestGameStateRekord.NAME)
            addField<TestGameStateRekord>(TestGameStateRekord.NAME)
        }
        RekordsStore(readdingSchema, rekordsEditor).count<TestGameStateRekord>().getOrThrow()
        assertEquals(expected = listOf(NAME_INDEX), actual = indexes(STATE_TABLE))
    }

    private fun indexes(table: String): List<String> {
        val connection = BundledSQLiteDriver().open(databaseFile.path)
        try {
            return connection.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ? " +
                    "AND name NOT LIKE 'sqlite_autoindex_%' ORDER BY name"
            ).use { statement ->
                statement.bindText(1, table)
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        } finally {
            connection.close()
        }
    }

    private companion object {
        const val PROPERTY_TABLE = "game_property"
        const val TITLE_INDEX = "idx_${PROPERTY_TABLE}_${TestGamePropertyRekord.TITLE}"
        const val STATE_TABLE = "game_state"
        const val NAME_INDEX = "idx_${STATE_TABLE}_${TestGameStateRekord.NAME}"
    }
}
