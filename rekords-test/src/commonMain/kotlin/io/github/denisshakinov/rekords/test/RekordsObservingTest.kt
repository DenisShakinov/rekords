package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.count
import io.github.denisshakinov.rekords.core.delete
import io.github.denisshakinov.rekords.core.deleteRekord
import io.github.denisshakinov.rekords.core.putRekord
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Observes a store over [rekordsEditor]: what an observer is handed first, and again once a
 * transaction writing what it observes commits - the rekords holding others included - and that a
 * transaction failing, or writing nothing it observes, hands it nothing.
 *
 * A write handing an observer nothing is followed by one that does, which the observer is to be
 * handed next: the rekords it read in between, had it read any, would come first.
 */
abstract class RekordsObservingTest(private val rekordsEditor: RekordsEditor) {

    private val rekordsStore = RekordsStore(TestShelfRekordsSchema(), rekordsEditor)

    private val france = TestCountryRekord(code = "fr", name = "France")
    private val dumas = TestAuthorRekord(id = 1, penName = "Dumas", country = france)
    private val musketeers = TestBookRekord(id = 10, title = "The Three Musketeers", author = dumas, coauthors = emptyList())
    private val monteCristo = TestBookRekord(id = 11, title = "Monte Cristo", author = dumas, coauthors = emptyList())

    private val classics = TestShelfRekord(
        id = 1,
        name = "Classics",
        featured = musketeers,
        latest = null,
        books = listOf(musketeers),
        curators = emptyList(),
    )
    private val modern = TestShelfRekord(
        id = 2,
        name = "Modern",
        featured = null,
        latest = null,
        books = emptyList(),
        curators = listOf(dumas),
    )

    private fun test(block: suspend TestScope.() -> Unit) = runTest {
        rekordsStore.transaction {
            delete<TestShelfRekord>()
            delete<TestBookRekord>()
            delete<TestAuthorRekord>()
            delete<TestCountryRekord>()
            putRekord(classics)
        }.getOrThrow()
        block()
    }

    private fun <T> TestScope.collected(flow: Flow<T>): ReceiveChannel<T> = flow.produceIn(backgroundScope)

    private fun shelves(): Flow<List<TestShelfRekord>> =
        rekordsStore.observeRekords<TestShelfRekord>(orderBy = listOf(Order.Ascending(TestShelfRekord.ID)))

    private fun books(): Flow<List<TestBookRekord>> =
        rekordsStore.observeRekords<TestBookRekord>(orderBy = listOf(Order.Ascending(TestBookRekord.ID)))

    open fun check_observer_is_handed_the_rekords_stored_first() = test {
        assertEquals(listOf(classics), shelves().first())
    }

    open fun check_observer_is_handed_the_rekords_again_once_put() = test {
        val shelves = collected(shelves())
        assertEquals(listOf(classics), shelves.receive())

        rekordsStore.putRekord(modern).getOrThrow()
        assertEquals(listOf(classics, modern), shelves.receive())

        val renamed = classics.copy(name = "Old classics")
        rekordsStore.putRekord(renamed).getOrThrow()
        assertEquals(listOf(renamed, modern), shelves.receive())
    }

    open fun check_observer_is_handed_the_rekords_again_once_deleted() = test {
        val shelves = collected(shelves())
        assertEquals(listOf(classics), shelves.receive())

        rekordsStore.deleteRekord(classics).getOrThrow()
        assertEquals(emptyList(), shelves.receive())
    }

    open fun check_observer_of_rekords_holding_others_is_handed_them_again_once_those_are_put() = test {
        val shelves = collected(shelves())
        assertEquals(listOf(classics), shelves.receive())

        val retitled = musketeers.copy(title = "Les Trois Mousquetaires")
        rekordsStore.putRekord(retitled).getOrThrow()
        assertEquals(listOf(classics.copy(featured = retitled, books = listOf(retitled))), shelves.receive())

        // Two levels deeper: the country of the author of the book.
        val renamed = france.copy(name = "République française")
        rekordsStore.putRekord(renamed).getOrThrow()
        val author = dumas.copy(country = renamed)
        val book = retitled.copy(author = author)
        assertEquals(listOf(classics.copy(featured = book, books = listOf(book))), shelves.receive())
    }

    open fun check_observer_of_nested_rekords_is_handed_them_again_once_rekords_holding_them_are_put() = test {
        val books = collected(books())
        assertEquals(listOf(musketeers), books.receive())

        rekordsStore.putRekord(classics.copy(books = listOf(musketeers, monteCristo))).getOrThrow()
        assertEquals(listOf(musketeers, monteCristo), books.receive())
    }

    open fun check_observer_is_handed_nothing_once_rekords_holding_its_own_are_deleted() = test {
        val books = collected(books())
        assertEquals(listOf(musketeers), books.receive())

        // The books a shelf holds stay stored once it is deleted.
        rekordsStore.delete<TestShelfRekord>().getOrThrow()
        rekordsStore.putRekord(monteCristo).getOrThrow()
        assertEquals(listOf(musketeers, monteCristo), books.receive())
    }

    open fun check_observer_is_handed_a_transaction_once() = test {
        val shelves = collected(shelves())
        assertEquals(listOf(classics), shelves.receive())

        rekordsStore.transaction {
            deleteRekord(classics)
            putRekord(modern)
            putRekord(musketeers.copy(title = "Twenty Years After"))
        }.getOrThrow()
        assertEquals(listOf(modern), shelves.receive())

        rekordsStore.putRekord(classics).getOrThrow()
        assertEquals(listOf(classics, modern), shelves.receive())
    }

    open fun check_observer_is_handed_nothing_of_a_failed_transaction() = test {
        val shelves = collected(shelves())
        assertEquals(listOf(classics), shelves.receive())

        val failed = rekordsStore.transaction {
            putRekord(modern)
            error("Rolled back")
        }
        assertEquals("Rolled back", failed.exceptionOrNull()?.message)

        val renamed = classics.copy(name = "Old classics")
        rekordsStore.putRekord(renamed).getOrThrow()
        assertEquals(listOf(renamed), shelves.receive())
    }

    open fun check_observers_of_a_rekord_and_a_count_follow_writes() = test {
        val shelf = collected(rekordsStore.observeRekord<TestShelfRekord>(Filter.Equals(TestShelfRekord.ID, 2L)))
        val count = collected(rekordsStore.observeCount<TestShelfRekord>())
        assertEquals(null, shelf.receive())
        assertEquals(1, count.receive())

        rekordsStore.putRekord(modern).getOrThrow()
        assertEquals(modern, shelf.receive())
        assertEquals(2, count.receive())
    }

    open fun check_observer_of_several_types_is_handed_what_it_reads_of_them() = test {
        val counts = collected(
            rekordsStore.observe(TestShelfRekord::class, TestCountryRekord::class) {
                count<TestShelfRekord>() to count<TestCountryRekord>()
            }
        )
        assertEquals(1 to 1, counts.receive())

        rekordsStore.putRekord(TestCountryRekord(code = "jp", name = "Japan")).getOrThrow()
        assertEquals(1 to 2, counts.receive())
    }

    open fun check_failed_read_fails_the_flow() = test {
        val failure = assertFailsWith<IllegalStateException> {
            rekordsStore.observe<Int>(TestShelfRekord::class) { error("Not read") }.first()
        }
        assertEquals("Not read", failure.message)
    }
}
