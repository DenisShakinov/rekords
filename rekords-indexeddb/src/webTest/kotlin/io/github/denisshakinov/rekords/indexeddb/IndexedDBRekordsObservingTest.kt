package io.github.denisshakinov.rekords.indexeddb

import io.github.denisshakinov.rekords.test.RekordsObservingTest
import kotlin.test.Test

/** Runs the shared observing suite against IndexedDB, a database of its own for each test. */
class IndexedDBRekordsObservingTest : RekordsObservingTest(IndexedDBRekordsEditor(nextObservingDatabaseName())) {

    @Test
    override fun check_observer_is_handed_the_rekords_stored_first() = super.check_observer_is_handed_the_rekords_stored_first()

    @Test
    override fun check_observer_is_handed_the_rekords_again_once_put() = super.check_observer_is_handed_the_rekords_again_once_put()

    @Test
    override fun check_observer_is_handed_the_rekords_again_once_deleted() = super.check_observer_is_handed_the_rekords_again_once_deleted()

    @Test
    override fun check_observer_of_rekords_holding_others_is_handed_them_again_once_those_are_put() = super.check_observer_of_rekords_holding_others_is_handed_them_again_once_those_are_put()

    @Test
    override fun check_observer_of_nested_rekords_is_handed_them_again_once_rekords_holding_them_are_put() = super.check_observer_of_nested_rekords_is_handed_them_again_once_rekords_holding_them_are_put()

    @Test
    override fun check_observer_is_handed_nothing_once_rekords_holding_its_own_are_deleted() = super.check_observer_is_handed_nothing_once_rekords_holding_its_own_are_deleted()

    @Test
    override fun check_observer_is_handed_a_transaction_once() = super.check_observer_is_handed_a_transaction_once()

    @Test
    override fun check_observer_is_handed_nothing_of_a_failed_transaction() = super.check_observer_is_handed_nothing_of_a_failed_transaction()

    @Test
    override fun check_observers_of_a_rekord_and_a_count_follow_writes() = super.check_observers_of_a_rekord_and_a_count_follow_writes()

    @Test
    override fun check_observer_of_several_types_is_handed_what_it_reads_of_them() = super.check_observer_of_several_types_is_handed_what_it_reads_of_them()

    @Test
    override fun check_failed_read_fails_the_flow() = super.check_failed_read_fails_the_flow()
}

private var databaseCount = 0

private fun nextObservingDatabaseName(): String {
    installFakeIndexedDB()
    return "rekords-observing-test-${databaseCount++}"
}
