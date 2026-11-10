package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.test.RekordsGeneratedIdTest
import kotlin.test.Test

/** Runs the shared suite of rekords whose ids are generated against the in-memory editor. */
class InMemoryRekordsGeneratedIdTest : RekordsGeneratedIdTest(InMemoryRekordsEditor()) {

    @Test
    override fun check_numbers_are_generated_one_after_another() = super.check_numbers_are_generated_one_after_another()

    @Test
    override fun check_rekords_put_together_are_given_numbers_of_their_own() = super.check_rekords_put_together_are_given_numbers_of_their_own()

    @Test
    override fun check_rekords_put_together_skip_the_numbers_they_hold() = super.check_rekords_put_together_skip_the_numbers_they_hold()

    @Test
    override fun check_number_of_a_rekord_deleted_is_not_given_again() = super.check_number_of_a_rekord_deleted_is_not_given_again()

    @Test
    override fun check_number_given_in_a_failed_transaction_is_given_again() = super.check_number_given_in_a_failed_transaction_is_given_again()

    @Test
    override fun check_rekord_held_is_not_given_the_number_another_one_held_holds() = super.check_rekord_held_is_not_given_the_number_another_one_held_holds()

    @Test
    override fun check_rekord_holding_its_id_is_updated() = super.check_rekord_holding_its_id_is_updated()

    @Test
    override fun check_number_follows_the_largest_stored() = super.check_number_follows_the_largest_stored()

    @Test
    override fun check_rekords_held_are_given_ids_along_with_the_one_holding_them() = super.check_rekords_held_are_given_ids_along_with_the_one_holding_them()

    @Test
    override fun check_rekord_held_with_its_id_is_shared() = super.check_rekord_held_with_its_id_is_shared()

    @Test
    override fun check_encrypted_id_is_generated_and_looked_up_by() = super.check_encrypted_id_is_generated_and_looked_up_by()
}
