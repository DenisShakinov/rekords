package io.github.denisshakinov.rekords.indexeddb

import io.github.denisshakinov.rekords.test.RekordsMigrationTest
import kotlin.test.Test

/** Runs the shared migration suite against IndexedDB, a database of its own for each test. */
class IndexedDBRekordsMigrationTest : RekordsMigrationTest(IndexedDBRekordsEditor(nextMigrationDatabaseName())) {

    @Test
    override fun check_field_added_by_a_migration_is_given_its_default_value() = super.check_field_added_by_a_migration_is_given_its_default_value()

    @Test
    override fun check_default_value_the_field_cannot_hold_fails_the_migration() = super.check_default_value_the_field_cannot_hold_fails_the_migration()

    @Test
    override fun check_field_renamed_by_a_migration_keeps_its_values_and_index() = super.check_field_renamed_by_a_migration_keeps_its_values_and_index()

    @Test
    override fun check_rekord_type_renamed_by_a_migration_keeps_its_rekords() = super.check_rekord_type_renamed_by_a_migration_keeps_its_rekords()

    @Test
    override fun check_rekord_type_removed_by_a_migration_takes_its_rekords() = super.check_rekord_type_removed_by_a_migration_takes_its_rekords()

    @Test
    override fun check_failed_migration_takes_back_the_changes_it_made() = super.check_failed_migration_takes_back_the_changes_it_made()
}

private var migrationDatabaseCount = 0

private fun nextMigrationDatabaseName(): String {
    installFakeIndexedDB()
    return "rekords-migration-test-${migrationDatabaseCount++}"
}
