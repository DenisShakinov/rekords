package io.github.denisshakinov.rekords.indexeddb

import io.github.denisshakinov.rekords.test.RekordsEditorTest
import kotlin.test.Test

/** Runs the shared rekords editor suite against IndexedDB, a database of its own for each test. */
class IndexedDBRekordsEditorTest : RekordsEditorTest(IndexedDBRekordsEditor(nextDatabaseName())) {

    @Test
    override fun check_RekordsStore_put_method() = super.check_RekordsStore_put_method()

    @Test
    override fun check_RekordsStore_query_method_with_nullable_date_filter() = super.check_RekordsStore_query_method_with_nullable_date_filter()

    @Test
    override fun check_RekordsStore_query_method_with_filter() = super.check_RekordsStore_query_method_with_filter()

    @Test
    override fun check_RekordsStore_query_method_with_orderBy() = super.check_RekordsStore_query_method_with_orderBy()

    @Test
    override fun check_RekordsStore_query_method_with_limit_and_offset() = super.check_RekordsStore_query_method_with_limit_and_offset()

    @Test
    override fun check_RekordsStore_delete_method() = super.check_RekordsStore_delete_method()

    @Test
    override fun check_RekordsStore_count_method() = super.check_RekordsStore_count_method()

    @Test
    override fun check_composite_state_is_read_correctly() = super.check_composite_state_is_read_correctly()

    @Test
    override fun check_composite_list_properties_are_read_correctly() = super.check_composite_list_properties_are_read_correctly()

    @Test
    override fun check_composite_state_normalization() = super.check_composite_state_normalization()

    @Test
    override fun check_composite_update_is_reflected_in_parent() = super.check_composite_update_is_reflected_in_parent()

    @Test
    override fun check_composite_shared_update_is_reflected_in_multiple_parents() = super.check_composite_shared_update_is_reflected_in_multiple_parents()

    @Test
    override fun check_composite_list_update_is_reflected_in_parent() = super.check_composite_list_update_is_reflected_in_parent()

    @Test
    override fun check_composite_insert_with_new_state() = super.check_composite_insert_with_new_state()

    @Test
    override fun check_filter_by_nested_field() = super.check_filter_by_nested_field()

    @Test
    override fun check_composite_delete_parent_does_not_affect_shared_state() = super.check_composite_delete_parent_does_not_affect_shared_state()

    @Test
    override fun check_composite_list_normalization() = super.check_composite_list_normalization()

    @Test
    override fun check_composite_list_shared_update_is_reflected_in_multiple_parents() = super.check_composite_list_shared_update_is_reflected_in_multiple_parents()

    @Test
    override fun check_composite_list_is_replaced_by_the_one_written() = super.check_composite_list_is_replaced_by_the_one_written()

    @Test
    override fun check_composite_list_delete_parent_does_not_affect_shared_properties() = super.check_composite_list_delete_parent_does_not_affect_shared_properties()

    @Test
    override fun check_composite_with_known_ids_is_updated_not_duplicated() = super.check_composite_with_known_ids_is_updated_not_duplicated()

    @Test
    override fun check_store_is_usable_after_close() = super.check_store_is_usable_after_close()

    @Test
    override fun check_transaction_applies_every_operation_once_it_completes() = super.check_transaction_applies_every_operation_once_it_completes()

    @Test
    override fun check_transaction_reads_back_what_it_has_written() = super.check_transaction_reads_back_what_it_has_written()

    @Test
    override fun check_transaction_is_rolled_back_when_it_fails() = super.check_transaction_is_rolled_back_when_it_fails()

    @Test
    override fun check_transaction_is_rolled_back_when_cancelled() = super.check_transaction_is_rolled_back_when_cancelled()

    @Test
    override fun check_cancelled_transaction_cancels_its_caller() = super.check_cancelled_transaction_cancels_its_caller()

    @Test
    override fun check_nested_transaction_joins_the_one_it_runs_in() = super.check_nested_transaction_joins_the_one_it_runs_in()

    @Test
    override fun check_failed_schema_upgrade_is_rolled_back() = super.check_failed_schema_upgrade_is_rolled_back()
}

private var databaseCount = 0

private fun nextDatabaseName(): String {
    installFakeIndexedDB()
    return "rekords-test-${databaseCount++}"
}
