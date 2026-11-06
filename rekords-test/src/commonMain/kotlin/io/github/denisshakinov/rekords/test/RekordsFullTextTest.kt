@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.addField
import io.github.denisshakinov.rekords.core.asRekordValue
import io.github.denisshakinov.rekords.core.delete
import io.github.denisshakinov.rekords.core.enableFullText
import io.github.denisshakinov.rekords.core.plus
import io.github.denisshakinov.rekords.core.renameField
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs a store of rekords found by the words of their fields over [rekordsEditor]: a SQL engine
 * keeps the words as they are written, the others find them in the rekords as they read them, and
 * both have to select the same rekords - and to keep doing so as rekords are written and fields
 * are migrated.
 */
abstract class RekordsFullTextTest(private val rekordsEditor: RekordsEditor) {

    private val rekordsStore = RekordsStore(TestFullTextRekordsSchema(), rekordsEditor)

    private val platformer = TestArticleTagRekord("platformer", "Platformer")
    private val racing = TestArticleTagRekord("racing", "Racing")
    private val rpg = TestArticleTagRekord("rpg", "Role-playing")

    private val marioBros = TestArticleRekord(1, "Super Mario Bros.", "A plumber saves a princess", "Ann", listOf(platformer))
    private val marioKart = TestArticleRekord(2, "Super Mario Kart", "Karting with Mario and friends", "Bob", listOf(racing))
    private val superstarSaga = TestArticleRekord(3, "Mario & Luigi: Superstar Saga", null, "Ann", listOf(rpg))
    private val doomEternal = TestArticleRekord(4, "Дум: Вечный", "Демоны на Марсе", "Вера", emptyList())
    private val supermarket = TestArticleRekord(5, "Supermarket Simulator", "Run a store", "Bob", emptyList())

    private fun test(block: suspend () -> Unit) = runTest {
        rekordsStore.transaction {
            delete<TestArticleRekord>()
            delete<TestArticleTagRekord>()
        }.getOrThrow()
        rekordsStore.putRekords(listOf(marioBros, marioKart, superstarSaga, doomEternal, supermarket)).getOrThrow()
        block()
    }

    private suspend fun ids(filter: Filter): List<Int> = rekordsStore
        .getRekords<TestArticleRekord>(filter, orderBy = listOf(Order.Ascending(TestArticleRekord.ID)))
        .getOrThrow()
        .map { it.id }

    open fun check_full_text_finds_every_word_as_a_prefix_in_any_order() = test {
        // Superstar begins with "sup" too; Supermarket holds no word beginning with "mar".
        assertEquals(expected = listOf(1, 2, 3), actual = ids(Filter.FullText("sup mar", TestArticleRekord.TITLE)))
        assertEquals(expected = listOf(1, 2, 3), actual = ids(Filter.FullText("mar sup", TestArticleRekord.TITLE)))
        assertEquals(expected = listOf(2), actual = ids(Filter.FullText("SUPER  mario, kart", TestArticleRekord.TITLE)))
        assertEquals(expected = listOf(5), actual = ids(Filter.FullText("supermarket", TestArticleRekord.TITLE)))
        // A word is found by its beginning, not by what it holds further on.
        assertEquals(expected = emptyList(), actual = ids(Filter.FullText("market", TestArticleRekord.TITLE)))
        // The case of any letter is ignored.
        assertEquals(expected = listOf(4), actual = ids(Filter.FullText("дум ВЕЧ", TestArticleRekord.TITLE)))
        // A query of no words selects every rekord.
        assertEquals(expected = listOf(1, 2, 3, 4, 5), actual = ids(Filter.FullText(" - ", TestArticleRekord.TITLE)))
    }

    open fun check_full_text_looks_into_every_field_given() = test {
        assertEquals(
            expected = listOf(1),
            actual = ids(Filter.FullText("mario plumber", TestArticleRekord.TITLE, TestArticleRekord.BODY)),
        )
        assertEquals(expected = emptyList(), actual = ids(Filter.FullText("mario plumber", TestArticleRekord.TITLE)))
        assertEquals(
            expected = listOf(4),
            actual = ids(Filter.FullText("марс", TestArticleRekord.TITLE, TestArticleRekord.BODY)),
        )
        // A field holding null holds no words.
        assertEquals(expected = listOf(5), actual = ids(Filter.FullText("run", TestArticleRekord.BODY)))
    }

    open fun check_full_text_combines_with_other_filters() = test {
        assertEquals(
            expected = listOf(4, 5),
            actual = ids(Filter.Not(Filter.WordPrefix(TestArticleRekord.TITLE, "Mario"))),
        )
        assertEquals(
            expected = listOf(2),
            actual = ids(Filter.Nested(TestArticleRekord.TAGS, Filter.WordPrefix(TestArticleTagRekord.NAME, "rac"))),
        )
        assertEquals(
            expected = listOf(3),
            actual = ids(Filter.FullText("mario", TestArticleRekord.TITLE) + Filter.Equals(TestArticleRekord.AUTHOR, "Ann") +
                Filter.Nested(TestArticleRekord.TAGS, Filter.WordPrefix(TestArticleTagRekord.NAME, "play"))),
        )
        assertEquals(
            expected = 3,
            actual = rekordsStore.count<TestArticleRekord>(Filter.FullText("mario", TestArticleRekord.TITLE)).getOrThrow(),
        )
        rekordsStore.delete<TestArticleRekord>(Filter.FullText("super", TestArticleRekord.TITLE)).getOrThrow()
        assertEquals(expected = listOf(4), actual = ids(Filter.FullText("", TestArticleRekord.TITLE)))
    }

    open fun check_full_text_follows_the_rekords_written() = test {
        rekordsStore.putRekord(supermarket.copy(title = "Mega Market")).getOrThrow()
        assertEquals(expected = listOf(5), actual = ids(Filter.FullText("mega", TestArticleRekord.TITLE)))
        assertEquals(expected = emptyList(), actual = ids(Filter.FullText("supermarket", TestArticleRekord.TITLE)))

        // A write of some fields to the rekords a filter selects keeps their words as well.
        rekordsStore.put<TestArticleRekord>(
            values = mapOf(TestArticleRekord.BODY to "Racing on rainbow roads".asRekordValue()),
            filter = Filter.FullText("kart", TestArticleRekord.TITLE),
        ).getOrThrow()
        assertEquals(expected = listOf(2), actual = ids(Filter.FullText("rainbow", TestArticleRekord.BODY)))
        assertEquals(expected = emptyList(), actual = ids(Filter.FullText("friends", TestArticleRekord.BODY)))
        rekordsStore.put<TestArticleRekord>(
            values = mapOf(TestArticleRekord.BODY to null),
            filter = Filter.Equals(TestArticleRekord.ID, 2),
        ).getOrThrow()
        assertEquals(expected = emptyList(), actual = ids(Filter.FullText("rainbow", TestArticleRekord.BODY)))
    }

    open fun check_full_text_on_a_field_not_marked_fails() = test {
        val failures = listOf(
            rekordsStore.getRekords<TestArticleRekord>(Filter.FullText("ann", TestArticleRekord.AUTHOR)).exceptionOrNull(),
            rekordsStore.count<TestArticleRekord>(Filter.Not(Filter.WordPrefix(TestArticleRekord.AUTHOR, "ann"))).exceptionOrNull(),
            rekordsStore.getRekords<TestArticleRekord>(
                Filter.Nested(TestArticleRekord.TAGS, Filter.WordPrefix(TestArticleTagRekord.ID, "rac")),
            ).exceptionOrNull(),
        )
        for (failure in failures) {
            assertTrue(failure is IllegalArgumentException, "Failed with $failure")
        }
    }

    /**
     * A field stored before it was fullText is made one by a migration, and one added, renamed or
     * removed by a migration takes its words along.
     */
    open fun check_full_text_is_kept_by_migrations() = test {
        val plainStore = upgrade(TestPlainNoteRekord::class) {}
        plainStore.putRekords(
            listOf(TestPlainNoteRekord(1, "Grocery list"), TestPlainNoteRekord(2, "Trip to Rome")),
        ).getOrThrow()

        val store = upgrade(TestNoteRekord::class) {
            enableFullText<TestNoteRekord>(TestNoteRekord.TITLE)
            addField<TestNoteRekord>(TestNoteRekord.SUMMARY, "Draft notes")
        }
        assertEquals(expected = listOf(2), actual = store.noteIds(Filter.FullText("rom tri", TestNoteRekord.TITLE)))
        assertEquals(expected = listOf(1, 2), actual = store.noteIds(Filter.FullText("draft", TestNoteRekord.SUMMARY)))

        val renamedStore = upgrade(TestNoteRekord::class) {
            renameField<TestNoteRekord>(TestNoteRekord.TITLE, "title_before")
            renameField<TestNoteRekord>("title_before", TestNoteRekord.TITLE)
        }
        assertEquals(expected = listOf(1), actual = renamedStore.noteIds(Filter.FullText("groc", TestNoteRekord.TITLE)))

        val readdedStore = upgrade(TestNoteRekord::class) {
            removeField("fts_note", TestNoteRekord.SUMMARY)
            addField<TestNoteRekord>(TestNoteRekord.SUMMARY, "Kept for later")
        }
        assertEquals(expected = emptyList(), actual = readdedStore.noteIds(Filter.FullText("draft", TestNoteRekord.SUMMARY)))
        assertEquals(expected = listOf(1, 2), actual = readdedStore.noteIds(Filter.FullText("later", TestNoteRekord.SUMMARY)))
    }

    private suspend fun upgrade(
        noteClass: KClass<*>,
        upgrade: suspend RekordsMigrationEditor.() -> Unit,
    ): RekordsStore {
        val version = rekordsEditor.schemaEditor.version()
        return RekordsStore(TestFullTextRekordsSchema(version + 1, noteClass, upgrade), rekordsEditor)
    }

    private suspend fun RekordsStore.noteIds(filter: Filter): List<Int> =
        getRekords<TestNoteRekord>(filter, orderBy = listOf(Order.Ascending(TestNoteRekord.ID)))
            .getOrThrow()
            .map { it.id }
}
