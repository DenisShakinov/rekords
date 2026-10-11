package io.github.denisshakinov.rekords.sqlite

import io.github.denisshakinov.rekords.test.RekordsNestedIdTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/** Runs the shared suite of rekords keyed by rekords against SQLite, through the bundled driver the JVM has. */
class JvmSQLiteRekordsNestedIdTest : RekordsNestedIdTest(
    SQLite.create { name = temporaryNestedIdDatabaseFile().path }
) {

    @Test
    override fun check_rekords_differing_in_the_rekord_held_as_an_id_are_kept_apart() = super.check_rekords_differing_in_the_rekord_held_as_an_id_are_kept_apart()

    @Test
    override fun check_rekord_written_again_under_the_same_rekord_held_is_updated() = super.check_rekord_written_again_under_the_same_rekord_held_is_updated()

    @Test
    override fun check_rekord_is_deleted_by_the_rekord_held_as_an_id() = super.check_rekord_is_deleted_by_the_rekord_held_as_an_id()

    @Test
    override fun check_rekord_held_as_an_id_updated_is_read_in_the_rekords_holding_it() = super.check_rekord_held_as_an_id_updated_is_read_in_the_rekords_holding_it()

    @Test
    override fun check_filter_on_the_rekord_held_as_an_id_selects_the_rekords_holding_it() = super.check_filter_on_the_rekord_held_as_an_id_selects_the_rekords_holding_it()

    @Test
    override fun check_rekords_keyed_by_rekords_keyed_by_rekords_are_kept_apart() = super.check_rekords_keyed_by_rekords_keyed_by_rekords_are_kept_apart()

    @Test
    override fun check_list_of_rekords_keyed_by_rekords_is_read_back_and_replaced() = super.check_list_of_rekords_keyed_by_rekords_is_read_back_and_replaced()
}

private fun temporaryNestedIdDatabaseFile(): File =
    Files.createTempDirectory("rekords").toFile().also { it.deleteOnExit() }.resolve("nested-id.db")
