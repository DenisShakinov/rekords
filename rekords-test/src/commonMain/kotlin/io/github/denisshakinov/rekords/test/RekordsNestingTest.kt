package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.or
import io.github.denisshakinov.rekords.core.plus
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals

/**
 * Runs a store of rekords nesting others over [rekordsEditor]: several levels deep, one type in
 * several fields, nested rekords that are null, and filters looking into any of them - which are to
 * select the rekords holding what they look for, and to leave what those rekords hold whole.
 */
abstract class RekordsNestingTest(private val rekordsEditor: RekordsEditor) {

    private val rekordsStore = RekordsStore(TestShelfRekordsSchema(), rekordsEditor)

    private val france = TestCountryRekord(code = "fr", name = "France")
    private val japan = TestCountryRekord(code = "jp", name = "Japan")

    private val dumas = TestAuthorRekord(id = 1, penName = "Dumas", country = france)
    private val maquet = TestAuthorRekord(id = 2, penName = "Maquet", country = france)
    private val murakami = TestAuthorRekord(id = 3, penName = "Murakami", country = japan)
    private val anonymous = TestAuthorRekord(id = 4, penName = "Anonymous", country = null)

    private val musketeers = TestBookRekord(id = 10, title = "The Three Musketeers", author = dumas, coauthors = listOf(maquet))
    private val monteCristo = TestBookRekord(id = 11, title = "Monte Cristo", author = dumas, coauthors = listOf(maquet, anonymous))
    private val norwegianWood = TestBookRekord(id = 12, title = "Norwegian Wood", author = murakami, coauthors = emptyList())
    private val folkTales = TestBookRekord(id = 13, title = "Folk Tales", author = null, coauthors = emptyList())

    private val shelves = listOf(
        TestShelfRekord(
            id = 1,
            name = "Classics",
            featured = monteCristo,
            latest = musketeers,
            // Not in the order of their ids, which a list is to be read back in all the same.
            books = listOf(monteCristo, musketeers, folkTales),
            curators = listOf(murakami, dumas),
        ),
        TestShelfRekord(
            id = 2,
            name = "Modern",
            featured = norwegianWood,
            latest = null,
            books = listOf(norwegianWood),
            curators = emptyList(),
        ),
        TestShelfRekord(
            id = 3,
            name = "Empty",
            featured = null,
            latest = null,
            books = emptyList(),
            curators = listOf(anonymous),
        ),
    )

    private fun test(block: suspend () -> Unit) = runTest {
        rekordsStore.delete<TestShelfRekord>().getOrThrow()
        rekordsStore.putRekords(shelves).getOrThrow()
        block()
    }

    private suspend fun allShelves(): List<TestShelfRekord> = shelves(filter = null)

    private suspend fun shelves(
        filter: Filter?,
        limit: Int? = null,
        offset: Int? = null,
    ): List<TestShelfRekord> = rekordsStore
        .getRekords<TestShelfRekord>(filter, orderBy = listOf(Order.Ascending(TestShelfRekord.ID)), limit, offset)
        .getOrThrow()

    private suspend fun ids(filter: Filter?, limit: Int? = null, offset: Int? = null): List<Long> =
        shelves(filter, limit, offset).map { it.id }

    open fun check_nested_rekords_are_read_back_as_written() = test {
        assertEquals(expected = shelves, actual = allShelves())
    }

    open fun check_nested_rekord_replaced_by_null_is_read_as_null() = test {
        val classics = shelves.first()
        val emptied = classics.copy(featured = null, latest = null, books = emptyList(), curators = emptyList())
        rekordsStore.putRekord(emptied).getOrThrow()
        val withoutAuthor = musketeers.copy(author = null)
        rekordsStore.putRekord(withoutAuthor).getOrThrow()

        assertEquals(expected = emptied, actual = shelves(Filter.Equals(TestShelfRekord.ID, 1L)).single())
        val book = rekordsStore.getRekord<TestBookRekord>(Filter.Equals(TestBookRekord.ID, 10L)).getOrThrow()
        assertEquals(expected = withoutAuthor, actual = book)
    }

    open fun check_nested_rekord_updated_is_read_in_every_rekord_holding_it() = test {
        val renamed = france.copy(name = "République française")
        rekordsStore.putRekord(renamed).getOrThrow()

        val classics = shelves(Filter.Equals(TestShelfRekord.ID, 1L)).single()
        assertEquals(expected = renamed, actual = classics.featured?.author?.country)
        assertEquals(expected = renamed, actual = classics.books[1].coauthors.single().country)
        assertEquals(expected = renamed, actual = classics.curators[1].country)
    }

    open fun check_filter_on_nested_list_selects_rekords_and_leaves_their_lists_whole() = test {
        val withFolkTales = Filter.Nested(TestShelfRekord.BOOKS, Filter.Equals(TestBookRekord.TITLE, "Folk Tales"))
        assertEquals(expected = listOf(shelves[0]), actual = shelves(withFolkTales))
    }

    open fun check_filter_on_each_field_of_one_type_looks_into_that_field_alone() = test {
        assertEquals(listOf(1L), ids(Filter.Nested(TestShelfRekord.FEATURED, Filter.Equals(TestBookRekord.ID, 11L))))
        assertEquals(emptyList(), ids(Filter.Nested(TestShelfRekord.LATEST, Filter.Equals(TestBookRekord.ID, 11L))))
        assertEquals(listOf(1L), ids(Filter.Nested(TestShelfRekord.LATEST, Filter.Equals(TestBookRekord.ID, 10L))))
        assertEquals(listOf(1L, 2L), ids(Filter.Nested(TestShelfRekord.BOOKS, Filter.Equals(TestBookRekord.ID, 12L)) or
            Filter.Nested(TestShelfRekord.FEATURED, Filter.Equals(TestBookRekord.ID, 11L))))
    }

    open fun check_filter_looks_into_rekords_nested_several_levels_deep() = test {
        val byJapanese = Filter.Nested(
            TestShelfRekord.BOOKS,
            Filter.Nested(TestBookRekord.AUTHOR, Filter.Nested(TestAuthorRekord.COUNTRY, Filter.Equals(TestCountryRekord.CODE, "jp"))),
        )
        assertEquals(listOf(2L), ids(byJapanese))

        val withAnonymousCoauthor = Filter.Nested(
            TestShelfRekord.BOOKS,
            Filter.Nested(TestBookRekord.COAUTHORS, Filter.Equals(TestAuthorRekord.PEN_NAME, "Anonymous")),
        )
        assertEquals(listOf(1L), ids(withAnonymousCoauthor))
    }

    open fun check_filter_on_nested_rekords_combines_with_others() = test {
        val byDumas = Filter.Nested(TestShelfRekord.CURATORS, Filter.Equals(TestAuthorRekord.PEN_NAME, "Dumas"))
        assertEquals(listOf(2L, 3L), ids(Filter.Not(byDumas)))
        assertEquals(emptyList(), ids(byDumas + Filter.Equals(TestShelfRekord.NAME, "Modern")))
        assertEquals(listOf(1L, 2L), ids(byDumas or Filter.Equals(TestShelfRekord.NAME, "Modern")))
    }

    open fun check_filter_on_nested_rekords_counts_each_rekord_once() = test {
        // Both of the books of the shelf are Dumas', which is one shelf all the same.
        val byDumas = Filter.Nested(TestShelfRekord.BOOKS, Filter.Nested(TestBookRekord.AUTHOR, Filter.Equals(TestAuthorRekord.ID, 1)))
        assertEquals(expected = 1, actual = rekordsStore.count<TestShelfRekord>(byDumas).getOrThrow())
        assertEquals(expected = listOf(1L), actual = ids(byDumas))
    }

    open fun check_filter_on_nested_rekords_pages_the_rekords_selected() = test {
        val withBooks = Filter.Nested(TestShelfRekord.BOOKS, Filter.Equals(TestBookRekord.TITLE, "Folk Tales")) or
            Filter.Nested(TestShelfRekord.FEATURED, Filter.Equals(TestBookRekord.TITLE, "Norwegian Wood"))
        assertEquals(listOf(1L, 2L), ids(withBooks))
        assertEquals(listOf(1L), ids(withBooks, limit = 1))
        assertEquals(listOf(2L), ids(withBooks, limit = 1, offset = 1))

        val first = rekordsStore
            .getRekord<TestShelfRekord>(Filter.Nested(TestShelfRekord.FEATURED, Filter.Equals(TestBookRekord.TITLE, "Norwegian Wood")))
            .getOrThrow()
        assertEquals(expected = shelves[1], actual = first)
    }

    open fun check_rekords_selected_by_nested_ones_are_deleted_and_those_alone() = test {
        rekordsStore.delete<TestShelfRekord>(
            Filter.Nested(TestShelfRekord.BOOKS, Filter.Equals(TestBookRekord.TITLE, "Norwegian Wood")),
        ).getOrThrow()
        assertEquals(listOf(1L, 3L), ids(null))
        assertEquals(shelves[0], shelves(Filter.Equals(TestShelfRekord.ID, 1L)).single())
        // The books are shared, and stay where other rekords may refer to them.
        assertEquals(4, rekordsStore.count<TestBookRekord>().getOrThrow())
    }

    open fun check_rekords_deleted_by_a_field_no_id_is_made_of() = test {
        rekordsStore.delete<TestShelfRekord>(Filter.Equals(TestShelfRekord.NAME, "Classics")).getOrThrow()
        assertEquals(listOf(2L, 3L), ids(null))
        // Written again, the shelf has the lists it is written with and nothing left of before.
        val rewritten = shelves[0].copy(books = listOf(folkTales), curators = emptyList())
        rekordsStore.putRekord(rewritten).getOrThrow()
        assertEquals(rewritten, shelves(Filter.Equals(TestShelfRekord.ID, 1L)).single())
    }
}
