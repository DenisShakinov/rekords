package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.asRekordValue
import io.github.denisshakinov.rekords.core.plus
import io.github.denisshakinov.rekords.test.TestGamePropertyRekord
import io.github.denisshakinov.rekords.test.TestGameRekord
import io.github.denisshakinov.rekords.test.TestGameStateRekord
import io.github.denisshakinov.rekords.test.TestRekordsSchema
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers what filing the rekords under their ids brings, which the shared suite has no way of
 * asking about: the editor answers the same as any other either way.
 */
class InMemoryRekordsKeyingTest {

    private val rekordsStore = RekordsStore(TestRekordsSchema(), InMemoryRekordsEditor())

    private fun game(id: Int = 1, title: String = "Doom", score: Float = 8.1f) = TestGameRekord(
        id = id,
        title = title,
        releaseDate = LocalDate(1993, 12, 10),
        score = score,
        state = TestGameStateRekord(id = 3, name = "Completed"),
        properties = emptyList(),
    )

    private fun genre(id: String = "fps", title: String = "FPS") = TestGamePropertyRekord(
        id = id,
        type = TestGamePropertyRekord.TYPE_GENRE,
        title = title,
        locale = "en",
    )

    /** The ids are what a rekord is, so writing it again is the one rekord taking new values. */
    @Test
    fun rekord_written_twice_under_the_same_ids_stays_one_rekord() = runTest {
        rekordsStore.putRekord(game())
        rekordsStore.putRekord(game(score = 9.5f))

        assertEquals(expected = 1, actual = rekordsStore.count<TestGameRekord>().getOrDefault(0))
        val stored = rekordsStore.getRekords<TestGameRekord>().getOrNull().orEmpty()
        assertEquals(expected = 9.5f, actual = stored.single().score)
    }

    /**
     * A rekord whose ids an update changes is filed under the ones it holds afterwards - keying it
     * by the ids it was stored with would otherwise leave it findable only by those.
     */
    @Test
    fun rekord_whose_id_field_is_updated_is_found_by_the_new_value() = runTest {
        rekordsStore.putRekord(game())

        rekordsStore.put<TestGameRekord>(
            values = mapOf(TestGameRekord.ID to 9.asRekordValue()),
            filter = Filter.Equals(TestGameRekord.ID, 1) + Filter.Equals(TestGameRekord.TITLE, "Doom"),
        )

        val byNewId = rekordsStore.getRekords<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 9))
            .getOrNull().orEmpty()
        assertEquals(expected = "Doom", actual = byNewId.singleOrNull()?.title)
        val byOldId = rekordsStore.getRekords<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
            .getOrNull().orEmpty()
        assertEquals(expected = emptyList(), actual = byOldId)
    }

    /**
     * An id field is worth a lookup of its own where there are several, since a filter is free to
     * name some and leave the rest to be looked through.
     */
    @Test
    fun rekord_is_found_by_one_of_the_several_fields_its_ids_are_made_of() = runTest {
        rekordsStore.putRekord(game(id = 1, title = "Doom"))
        rekordsStore.putRekord(game(id = 2, title = "Doom 2"))
        rekordsStore.putRekord(game(id = 3, title = "Quake"))

        val byId = rekordsStore.getRekords<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 2))
            .getOrNull().orEmpty()
        assertEquals(expected = "Doom 2", actual = byId.singleOrNull()?.title)
    }

    /** A filter naming several values of a field asks for the rekords of any one of them. */
    @Test
    fun rekords_are_found_by_lists_of_id_values() = runTest {
        rekordsStore.putRekord(game(id = 1, title = "Doom"))
        rekordsStore.putRekord(game(id = 2, title = "Doom 2"))
        rekordsStore.putRekord(game(id = 3, title = "Quake"))

        val found = rekordsStore.getRekords<TestGameRekord>(
            filter = Filter.InList(TestGameRekord.ID, listOf(1, 3)) +
                    Filter.InList(TestGameRekord.TITLE, listOf("Doom", "Quake")),
        ).getOrNull().orEmpty()
        assertEquals(expected = setOf("Doom", "Quake"), actual = found.map { it.title }.toSet())
    }

    /**
     * A searchable field is indexed as an id field is, and an update has to leave the index
     * holding the value the rekord holds - the rekord would otherwise still be found by the value
     * it was written with.
     */
    @Test
    fun searchable_field_finds_the_rekord_by_the_value_it_holds_now() = runTest {
        rekordsStore.putRekord(genre(title = "FPS"))

        rekordsStore.put<TestGamePropertyRekord>(
            values = mapOf(TestGamePropertyRekord.TITLE to "Shooter".asRekordValue()),
            filter = Filter.Equals(TestGamePropertyRekord.ID, "fps") +
                    Filter.Equals(TestGamePropertyRekord.TYPE, TestGamePropertyRekord.TYPE_GENRE),
        )

        val byOldTitle = rekordsStore
            .getRekords<TestGamePropertyRekord>(Filter.Equals(TestGamePropertyRekord.TITLE, "FPS"))
            .getOrNull().orEmpty()
        assertEquals(expected = emptyList(), actual = byOldTitle)
        val byNewTitle = rekordsStore
            .getRekords<TestGamePropertyRekord>(
                Filter.Equals(TestGamePropertyRekord.TITLE, "Shooter")
            )
            .getOrNull().orEmpty()
        assertEquals(expected = "fps", actual = byNewTitle.singleOrNull()?.id)
    }

    /** A rekord deleted is a rekord gone from the indexes too, not one found by a stale value. */
    @Test
    fun deleted_rekord_is_no_longer_found_by_an_indexed_value() = runTest {
        rekordsStore.putRekord(genre(id = "fps", title = "FPS"))
        rekordsStore.putRekord(genre(id = "rpg", title = "RPG"))

        rekordsStore.deleteRekord(genre(id = "fps", title = "FPS"))

        val byTitle = rekordsStore
            .getRekords<TestGamePropertyRekord>(Filter.Equals(TestGamePropertyRekord.TITLE, "FPS"))
            .getOrNull().orEmpty()
        assertEquals(expected = emptyList(), actual = byTitle)
        assertEquals(
            expected = 1,
            actual = rekordsStore.count<TestGamePropertyRekord>().getOrDefault(0),
        )
    }
}
