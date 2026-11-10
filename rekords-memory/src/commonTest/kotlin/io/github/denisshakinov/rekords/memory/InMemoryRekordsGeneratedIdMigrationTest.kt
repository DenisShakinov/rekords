package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.test.RekordsGeneratedIdMigrationTest
import kotlin.test.Test

/** Runs the shared suite of generated ids through a migration against the in-memory editor. */
class InMemoryRekordsGeneratedIdMigrationTest : RekordsGeneratedIdMigrationTest(InMemoryRekordsEditor()) {

    @Test
    override fun check_numbers_given_go_with_a_rekord_type_renamed_or_removed() = super.check_numbers_given_go_with_a_rekord_type_renamed_or_removed()
}
