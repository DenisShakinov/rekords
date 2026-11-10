package io.github.denisshakinov.rekords.indexeddb

import io.github.denisshakinov.rekords.test.RekordsGeneratedIdMigrationTest
import kotlin.test.Test

/** Runs the shared suite of generated ids through a migration against IndexedDB, a database of its own for each test. */
class IndexedDBRekordsGeneratedIdMigrationTest :
    RekordsGeneratedIdMigrationTest(IndexedDBRekordsEditor(nextGeneratedIdMigrationDatabaseName())) {

    @Test
    override fun check_numbers_given_go_with_a_rekord_type_renamed_or_removed() = super.check_numbers_given_go_with_a_rekord_type_renamed_or_removed()
}

private var databaseCount = 0

private fun nextGeneratedIdMigrationDatabaseName(): String {
    installFakeIndexedDB()
    return "rekords-generated-id-migration-test-${databaseCount++}"
}
