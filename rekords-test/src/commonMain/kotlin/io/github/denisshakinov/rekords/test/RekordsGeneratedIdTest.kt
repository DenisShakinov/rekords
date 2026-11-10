package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.delete
import io.github.denisshakinov.rekords.core.putRekord
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Runs a store of rekords whose ids are generated over [rekordsEditor]: numbers following the
 * largest stored, UUIDs - one of them encrypted - and the ids of the rekords a rekord holds, given
 * along with its own. The store hands each rekord back as stored, which is to be what is read.
 */
abstract class RekordsGeneratedIdTest(private val rekordsEditor: RekordsEditor) {

    private val rekordsStore =
        RekordsStore(TestGeneratedIdRekordsSchema(), rekordsEditor, cipher = TestRekordsCipher())

    private fun test(block: suspend () -> Unit) = runTest {
        rekordsStore.transaction {
            delete<TestMemoRekord>()
            delete<TestNovelRekord>()
            delete<TestSectionRekord>()
            delete<TestWriterRekord>()
        }.getOrThrow()
        block()
    }

    private suspend fun writers(): List<TestWriterRekord> = rekordsStore
        .getRekords<TestWriterRekord>(orderBy = listOf(Order.Ascending(TestWriterRekord.ID)))
        .getOrThrow()

    open fun check_numbers_are_generated_one_after_another() = test {
        val first = rekordsStore.putRekord(TestWriterRekord(name = "First")).getOrThrow()
        val second = rekordsStore.putRekord(TestWriterRekord(name = "Second")).getOrThrow()

        assertEquals(expected = TestWriterRekord(id = 1, name = "First"), actual = first)
        assertEquals(expected = TestWriterRekord(id = 2, name = "Second"), actual = second)
        assertEquals(expected = listOf(first, second), actual = writers())
    }

    open fun check_rekords_put_together_are_given_numbers_of_their_own() = test {
        val stored = rekordsStore
            .putRekords(listOf(TestWriterRekord(name = "First"), TestWriterRekord(name = "Second")))
            .getOrThrow()

        assertEquals(expected = listOf(1L, 2L), actual = stored.map { it.id })
        assertEquals(expected = stored, actual = writers())
    }

    open fun check_rekords_put_together_skip_the_numbers_they_hold() = test {
        val stored = rekordsStore
            .putRekords(
                listOf(
                    TestWriterRekord(name = "First"),
                    TestWriterRekord(id = 2, name = "Given"),
                    TestWriterRekord(name = "Third"),
                )
            )
            .getOrThrow()

        assertEquals(expected = listOf(1L, 2L, 3L), actual = stored.map { it.id })
        assertEquals(expected = stored, actual = writers())
    }

    open fun check_number_of_a_rekord_deleted_is_not_given_again() = test {
        rekordsStore.putRekords(listOf(TestWriterRekord(name = "First"), TestWriterRekord(name = "Second"))).getOrThrow()
        rekordsStore.delete<TestWriterRekord>(Filter.Equals(TestWriterRekord.ID, 2L)).getOrThrow()

        val third = rekordsStore.putRekord(TestWriterRekord(name = "Third")).getOrThrow()

        assertEquals(expected = 3L, actual = third.id)
    }

    open fun check_number_given_in_a_failed_transaction_is_given_again() = test {
        val failed = rekordsStore.transaction {
            putRekord(TestWriterRekord(name = "Lost"))
            error("Failed after the number was given")
        }
        assertTrue(failed.isFailure)

        val first = rekordsStore.putRekord(TestWriterRekord(name = "First")).getOrThrow()

        assertEquals(expected = 1L, actual = first.id)
    }

    open fun check_rekord_held_is_not_given_the_number_another_one_held_holds() = test {
        val novel = TestNovelRekord(
            title = "Novel",
            writer = TestWriterRekord(name = "Writer"),
            sections = listOf(TestSectionRekord(title = "New"), TestSectionRekord(id = 1, title = "Given")),
        )
        val stored = rekordsStore.putRekord(novel).getOrThrow()

        assertEquals(expected = listOf(2, 1), actual = stored.sections.map { it.id })
        assertEquals(
            expected = stored,
            actual = rekordsStore.getRekord<TestNovelRekord>(Filter.Equals(TestNovelRekord.ID, stored.id)).getOrThrow(),
        )
    }

    open fun check_rekord_holding_its_id_is_updated() = test {
        val stored = rekordsStore.putRekord(TestWriterRekord(name = "Writer")).getOrThrow()
        val renamed = stored.copy(name = "Renamed")

        assertSame(expected = renamed, actual = rekordsStore.putRekord(renamed).getOrThrow())
        assertEquals(expected = listOf(renamed), actual = writers())
    }

    open fun check_number_follows_the_largest_stored() = test {
        rekordsStore.putRekord(TestWriterRekord(id = 10, name = "Given")).getOrThrow()
        val generated = rekordsStore.putRekord(TestWriterRekord(name = "Generated")).getOrThrow()

        assertEquals(expected = 11L, actual = generated.id)
    }

    open fun check_rekords_held_are_given_ids_along_with_the_one_holding_them() = test {
        val novel = TestNovelRekord(
            title = "Novel",
            writer = TestWriterRekord(name = "Writer"),
            sections = listOf(TestSectionRekord(title = "One"), TestSectionRekord(title = "Two")),
        )
        val stored = rekordsStore.putRekord(novel).getOrThrow()

        assertEquals(expected = UUID_LENGTH, actual = stored.id.length)
        assertEquals(expected = 1L, actual = stored.writer.id)
        assertEquals(expected = listOf(1, 2), actual = stored.sections.map { it.id })
        assertEquals(
            expected = stored,
            actual = rekordsStore.getRekord<TestNovelRekord>(Filter.Equals(TestNovelRekord.ID, stored.id)).getOrThrow(),
        )
    }

    open fun check_rekord_held_with_its_id_is_shared() = test {
        val writer = rekordsStore.putRekord(TestWriterRekord(name = "Writer")).getOrThrow()
        val first = rekordsStore.putRekord(TestNovelRekord(title = "First", writer = writer, sections = emptyList())).getOrThrow()
        val second = rekordsStore.putRekord(TestNovelRekord(title = "Second", writer = writer, sections = emptyList())).getOrThrow()

        assertNotEquals(illegal = first.id, actual = second.id)
        assertEquals(expected = listOf(writer), actual = writers())
        assertEquals(expected = 2, actual = rekordsStore.count<TestNovelRekord>().getOrThrow())
    }

    open fun check_encrypted_id_is_generated_and_looked_up_by() = test {
        val stored = rekordsStore.putRekord(TestMemoRekord(text = "Secret")).getOrThrow()

        assertEquals(expected = UUID_LENGTH, actual = stored.id.length)
        assertEquals(
            expected = stored,
            actual = rekordsStore.getRekord<TestMemoRekord>(Filter.Equals(TestMemoRekord.ID, stored.id)).getOrThrow(),
        )
    }

    private companion object {
        const val UUID_LENGTH = 36
    }
}
