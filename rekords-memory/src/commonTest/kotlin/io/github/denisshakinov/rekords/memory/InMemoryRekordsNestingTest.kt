package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.test.RekordsNestingTest
import kotlin.test.Test

/** Runs the shared nesting suite against the in-memory editor. */
class InMemoryRekordsNestingTest : RekordsNestingTest(InMemoryRekordsEditor()) {

    @Test
    override fun check_nested_rekords_are_read_back_as_written() = super.check_nested_rekords_are_read_back_as_written()

    @Test
    override fun check_nested_rekord_replaced_by_null_is_read_as_null() = super.check_nested_rekord_replaced_by_null_is_read_as_null()

    @Test
    override fun check_nested_rekord_updated_is_read_in_every_rekord_holding_it() = super.check_nested_rekord_updated_is_read_in_every_rekord_holding_it()

    @Test
    override fun check_filter_on_nested_list_selects_rekords_and_leaves_their_lists_whole() = super.check_filter_on_nested_list_selects_rekords_and_leaves_their_lists_whole()

    @Test
    override fun check_filter_on_each_field_of_one_type_looks_into_that_field_alone() = super.check_filter_on_each_field_of_one_type_looks_into_that_field_alone()

    @Test
    override fun check_filter_looks_into_rekords_nested_several_levels_deep() = super.check_filter_looks_into_rekords_nested_several_levels_deep()

    @Test
    override fun check_filter_on_nested_rekords_combines_with_others() = super.check_filter_on_nested_rekords_combines_with_others()

    @Test
    override fun check_filter_on_nested_rekords_counts_each_rekord_once() = super.check_filter_on_nested_rekords_counts_each_rekord_once()

    @Test
    override fun check_filter_on_nested_rekords_pages_the_rekords_selected() = super.check_filter_on_nested_rekords_pages_the_rekords_selected()

    @Test
    override fun check_rekords_selected_by_nested_ones_are_deleted_and_those_alone() = super.check_rekords_selected_by_nested_ones_are_deleted_and_those_alone()

    @Test
    override fun check_rekords_deleted_by_a_field_no_id_is_made_of() = super.check_rekords_deleted_by_a_field_no_id_is_made_of()
}
