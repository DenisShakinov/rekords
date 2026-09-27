@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sqlite

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.addField
import io.github.denisshakinov.rekords.core.asRekordValue
import io.github.denisshakinov.rekords.core.encode
import io.github.denisshakinov.rekords.core.removeField
import io.github.denisshakinov.rekords.test.RekordsEditorTest
import io.github.denisshakinov.rekords.test.TestGamePropertyRekord
import io.github.denisshakinov.rekords.test.TestGameRekord
import io.github.denisshakinov.rekords.test.TestGameStateRekord
import io.github.denisshakinov.rekords.test.TestRekordsSchema
import io.github.denisshakinov.rekords.test.TestUpgradingSchema
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the shared rekords editor suite against a SQLite database.
 *
 * The editor is passed in because each target builds one differently: Android needs a [android
 * context][android.content.Context], the JVM a file, the native targets only a name.
 */
abstract class SQLiteRekordsEditorTest(private val rekordsEditor: RekordsEditor) :
    RekordsEditorTest(rekordsEditor) {

    /**
     * A table a migration rebuilds - renaming a field is done that way - keeps the rekords it held
     * and the constraints its columns were declared with. A column that had lost its NOT NULL
     * would take in a rekord written without a value it cannot do without.
     */
    @Test
    fun table_rebuilt_by_a_migration_keeps_its_rekords_and_constraints() = runTest {
        val store = RekordsStore(TestRekordsSchema(), rekordsEditor)
        store.delete<TestGameRekord>().getOrThrow()
        store.putRekord(game(id = 1, title = "Doom")).getOrThrow()

        // Renamed and back: the rekords come out as they went in, from a table rebuilt twice.
        val version = rekordsEditor.schemaEditor.version()
        val upgradedStore = RekordsStore(
            TestUpgradingSchema(version = version + 1) {
                renameField(TestGameRekord.REKORD_TYPE, TestGameRekord.SCORE, RENAMED_SCORE)
                renameField(TestGameRekord.REKORD_TYPE, RENAMED_SCORE, TestGameRekord.SCORE)
            },
            rekordsEditor,
        )
        val doom = upgradedStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
        assertEquals(expected = 9.5f, actual = doom.getOrThrow()?.score)

        upgradedStore.put<TestGameRekord>(
            values = mapOf(
                TestGameRekord.ID to 2.asRekordValue(),
                TestGameRekord.TITLE to "Doom 2".asRekordValue(),
            ),
            filter = Filter.Equals(TestGameRekord.ID, 2),
        ).getOrThrow()
        assertEquals(expected = 1, actual = upgradedStore.count<TestGameRekord>().getOrThrow())
    }

    /**
     * A field a migration adds that cannot be null is given, in the rekords already stored, the
     * value of its type that stands for none - and is otherwise the column it would have been had
     * the table been created with it: a rekord written without it is not stored.
     */
    @Test
    fun non_null_field_added_by_a_migration_is_filled_and_kept_not_null() = runTest {
        val store = RekordsStore(TestRekordsSchema(), rekordsEditor)
        store.delete<TestGameRekord>().getOrThrow()
        store.putRekord(game(id = 1, title = "Doom")).getOrThrow()

        val version = rekordsEditor.schemaEditor.version()
        val upgradedStore = RekordsStore(
            TestUpgradingSchema(version = version + 1) {
                removeField<TestGameRekord>(TestGameRekord.RELEASE_DATE)
                addField<TestGameRekord>(TestGameRekord.RELEASE_DATE)
                removeField<TestGameRekord>(TestGameRekord.FINISHED)
                addField<TestGameRekord>(TestGameRekord.FINISHED)
                removeField<TestGameRekord>(TestGameRekord.SCORE)
                addField<TestGameRekord>(TestGameRekord.SCORE)
                removeField<TestGamePropertyRekord>(TestGamePropertyRekord.LOCALE)
                addField<TestGamePropertyRekord>(TestGamePropertyRekord.LOCALE)
            },
            rekordsEditor,
        )
        val doom = upgradedStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
            .getOrThrow()
        assertEquals(expected = LocalDate(1970, 1, 1), actual = doom?.releaseDate)
        assertEquals(expected = false, actual = doom?.finished)
        assertEquals(expected = 0f, actual = doom?.score)
        assertEquals(expected = listOf(""), actual = doom?.properties?.map { it.locale })
        assertEquals(expected = "Completed", actual = doom?.state?.name)

        upgradedStore.put<TestGameRekord>(
            values = encode(game(id = 2, title = "Doom 2")) - TestGameRekord.SCORE,
            filter = Filter.Equals(TestGameRekord.ID, 2),
        ).getOrThrow()
        assertEquals(expected = 1, actual = upgradedStore.count<TestGameRekord>().getOrThrow())
    }

    /**
     * A field a migration adds is given, in the rekords already stored, the default value it is
     * added with, of the type the field is declared with - whether it can be null or not. A
     * nullable one is otherwise left as though the table had been created with it: a rekord written
     * without it holds none, rather than the default.
     */
    @Test
    fun field_added_by_a_migration_is_given_the_default_value_passed() = runTest {
        val store = RekordsStore(TestRekordsSchema(), rekordsEditor)
        store.delete<TestGameRekord>().getOrThrow()
        store.putRekord(game(id = 1, title = "Doom")).getOrThrow()

        val version = rekordsEditor.schemaEditor.version()
        val upgradedStore = RekordsStore(
            TestUpgradingSchema(version = version + 1) {
                removeField<TestGameRekord>(TestGameRekord.RELEASE_DATE)
                addField<TestGameRekord>(TestGameRekord.RELEASE_DATE, LocalDate(2000, 1, 1))
                removeField<TestGameRekord>(TestGameRekord.SCORE)
                addField<TestGameRekord>(TestGameRekord.SCORE, 7.5f)
                removeField<TestGameRekord>(TestGameRekord.REMASTER_DATE)
                addField<TestGameRekord>(TestGameRekord.REMASTER_DATE)
                removeField<TestGameStateRekord>(TestGameStateRekord.NAME)
                addField<TestGameStateRekord>(TestGameStateRekord.NAME, "Unknown")
            },
            rekordsEditor,
        )
        val doom = upgradedStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
            .getOrThrow()
        assertEquals(expected = LocalDate(2000, 1, 1), actual = doom?.releaseDate)
        assertEquals(expected = 7.5f, actual = doom?.score)
        assertEquals(expected = null, actual = doom?.remasterDate)
        assertEquals(expected = "Unknown", actual = doom?.state?.name)

        upgradedStore.put<TestGameStateRekord>(
            values = mapOf(TestGameStateRekord.ID to 9.asRekordValue()),
            filter = Filter.Equals(TestGameStateRekord.ID, 9),
        ).getOrThrow()
        val state = upgradedStore
            .getRekord<TestGameStateRekord>(Filter.Equals(TestGameStateRekord.ID, 9))
            .getOrThrow()
        assertEquals(expected = 9, actual = state?.id)
        assertEquals(expected = null, actual = state?.name)
    }

    /**
     * A default value a field cannot hold fails the migration, which leaves the storage as it was
     * rather than with a value that would only be found out when the rekord holding it is read.
     */
    @Test
    fun default_value_the_field_cannot_hold_fails_the_migration() = runTest {
        val store = RekordsStore(TestRekordsSchema(), rekordsEditor)
        store.delete<TestGameRekord>().getOrThrow()
        store.putRekord(game(id = 1, title = "Doom")).getOrThrow()

        val version = rekordsEditor.schemaEditor.version()
        val upgradedStore = RekordsStore(
            TestUpgradingSchema(version = version + 1) {
                removeField<TestGameRekord>(TestGameRekord.SCORE)
                addField<TestGameRekord>(TestGameRekord.SCORE, "high")
            },
            rekordsEditor,
        )
        val failure = upgradedStore.count<TestGameRekord>().exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "Failed with $failure")
        assertEquals(expected = version, actual = rekordsEditor.schemaEditor.version())
        val doom = store.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
        assertEquals(expected = 9.5f, actual = doom.getOrThrow()?.score)
    }

    private fun game(id: Int, title: String) = TestGameRekord(
        id = id,
        title = title,
        releaseDate = LocalDate(1993, 12, 10),
        remasterDate = LocalDate(2019, 7, 26),
        finished = true,
        score = 9.5f,
        state = TestGameStateRekord(id = 3, name = "Completed"),
        properties = listOf(
            TestGamePropertyRekord(
                id = "fps",
                type = TestGamePropertyRekord.TYPE_GENRE,
                title = "FPS",
                locale = "en",
            ),
        ),
    )

    private companion object {
        const val RENAMED_SCORE = "score_before"
    }

    @Test
    override fun check_RekordsStore_put_method() = super.check_RekordsStore_put_method()

    @Test
    override fun check_RekordsStore_query_method_with_filter() =
        super.check_RekordsStore_query_method_with_filter()

    @Test
    override fun check_RekordsStore_query_method_with_nullable_date_filter() =
        super.check_RekordsStore_query_method_with_nullable_date_filter()

    @Test
    override fun check_RekordsStore_query_method_with_orderBy() =
        super.check_RekordsStore_query_method_with_orderBy()

    @Test
    override fun check_RekordsStore_query_method_with_limit_and_offset() =
        super.check_RekordsStore_query_method_with_limit_and_offset()

    @Test
    override fun check_RekordsStore_delete_method() = super.check_RekordsStore_delete_method()

    @Test
    override fun check_RekordsStore_count_method() = super.check_RekordsStore_count_method()

    @Test
    override fun check_composite_state_is_read_correctly() =
        super.check_composite_state_is_read_correctly()

    @Test
    override fun check_composite_list_properties_are_read_correctly() =
        super.check_composite_list_properties_are_read_correctly()

    @Test
    override fun check_composite_state_normalization() = super.check_composite_state_normalization()

    @Test
    override fun check_composite_update_is_reflected_in_parent() =
        super.check_composite_update_is_reflected_in_parent()

    @Test
    override fun check_composite_shared_update_is_reflected_in_multiple_parents() =
        super.check_composite_shared_update_is_reflected_in_multiple_parents()

    @Test
    override fun check_composite_list_update_is_reflected_in_parent() =
        super.check_composite_list_update_is_reflected_in_parent()

    @Test
    override fun check_composite_insert_with_new_state() =
        super.check_composite_insert_with_new_state()

    @Test
    override fun check_filter_by_nested_field() = super.check_filter_by_nested_field()

    @Test
    override fun check_composite_delete_parent_does_not_affect_shared_state() =
        super.check_composite_delete_parent_does_not_affect_shared_state()

    @Test
    override fun check_composite_list_normalization() = super.check_composite_list_normalization()

    @Test
    override fun check_composite_list_shared_update_is_reflected_in_multiple_parents() =
        super.check_composite_list_shared_update_is_reflected_in_multiple_parents()

    @Test
    override fun check_composite_list_is_replaced_by_the_one_written() =
        super.check_composite_list_is_replaced_by_the_one_written()

    @Test
    override fun check_composite_list_delete_parent_does_not_affect_shared_properties() =
        super.check_composite_list_delete_parent_does_not_affect_shared_properties()

    @Test
    override fun check_composite_with_known_ids_is_updated_not_duplicated() =
        super.check_composite_with_known_ids_is_updated_not_duplicated()

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
