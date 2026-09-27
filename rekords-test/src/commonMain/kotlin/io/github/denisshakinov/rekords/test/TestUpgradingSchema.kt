package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema

/**
 * The test schema at [version], which [upgrade] brings a storage of an older version up to.
 *
 * A test gives it the version after the one the storage is at, rather than a fixed one: the tests
 * of a run on a device share the database it keeps, and a fixed version one of them has already
 * upgraded it to would not be upgraded to again.
 */
class TestUpgradingSchema(
    override val version: Int,
    private val upgrade: suspend RekordsMigrationEditor.() -> Unit,
) : RekordsSchema by TestRekordsSchema() {

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = upgrade()
}
