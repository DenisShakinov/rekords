package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.delete
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals

/**
 * Runs a store of rekords holding embedded classes over [rekordsEditor]: one embedded class kept
 * in two types - under its own names in one, under the field's in the other - as an id in both,
 * and one that may be none, holding a rekord. They are to be read back as written, told apart by
 * what they embed, and filtered by it whether a filter looks into it or names its fields.
 */
abstract class RekordsEmbeddedTest(private val rekordsEditor: RekordsEditor) {

    private val rekordsStore = RekordsStore(TestEmbeddedRekordsSchema(), rekordsEditor)

    private val pc = TestPlatformRekord(id = "pc", kind = 1, title = "PC")
    private val console = TestPlatformRekord(id = "ps5", kind = 1, title = "PlayStation 5")

    private val doomKey = TestGameKey(id = "Q1", idType = "wikidata")
    // The same id under another type: only both fields together tell the two games apart.
    private val doomOnIgdbKey = TestGameKey(id = "Q1", idType = "igdb")
    private val quakeKey = TestGameKey(id = "Q2", idType = "wikidata")

    private val doom = TestKeyedGameRekord(doomKey, "Doom", TestRelease(LocalDate(1993, 12, 10), pc), ports = listOf(console))
    private val doomOnIgdb = TestKeyedGameRekord(doomOnIgdbKey, "Doom (IGDB)", TestRelease(LocalDate(1993, 12, 11), platform = null), ports = listOf(pc, console))
    private val quake = TestKeyedGameRekord(quakeKey, "Quake", release = null, ports = emptyList())

    private val doomOnPc = TestPlayRekord(doomKey, pc, hours = 10)
    private val doomOnConsole = TestPlayRekord(doomKey, console, hours = 20)
    private val quakeOnPc = TestPlayRekord(quakeKey, pc, hours = 30)

    private fun test(block: suspend () -> Unit) = runTest {
        rekordsStore.transaction {
            delete<TestPlayRekord>()
            delete<TestKeyedGameRekord>()
            delete<TestPlatformRekord>()
        }.getOrThrow()
        rekordsStore.putRekords(listOf(doom, doomOnIgdb, quake)).getOrThrow()
        rekordsStore.putRekords(listOf(doomOnPc, doomOnConsole, quakeOnPc)).getOrThrow()
        block()
    }

    private suspend fun games(filter: Filter? = null): List<TestKeyedGameRekord> = rekordsStore
        .getRekords<TestKeyedGameRekord>(filter, orderBy = listOf(Order.Ascending(TestKeyedGameRekord.TITLE)))
        .getOrThrow()

    private suspend fun plays(filter: Filter? = null): List<TestPlayRekord> = rekordsStore
        .getRekords<TestPlayRekord>(filter, orderBy = listOf(Order.Ascending(TestPlayRekord.HOURS)))
        .getOrThrow()

    open fun check_embedded_classes_are_read_back_as_written() = test {
        assertEquals(expected = listOf(doom, doomOnIgdb, quake), actual = games())
        assertEquals(expected = listOf(doomOnPc, doomOnConsole, quakeOnPc), actual = plays())
    }

    open fun check_rekord_written_again_under_the_same_embedded_id_is_updated() = test {
        val retitled = doom.copy(title = "Doom (1993)", release = null, ports = listOf(pc))
        rekordsStore.putRekord(retitled).getOrThrow()
        val longer = doomOnConsole.copy(hours = 25)
        rekordsStore.putRekord(longer).getOrThrow()

        assertEquals(expected = listOf(retitled, doomOnIgdb, quake), actual = games())
        assertEquals(expected = listOf(doomOnPc, longer, quakeOnPc), actual = plays())
    }

    open fun check_rekord_is_deleted_by_its_embedded_id() = test {
        rekordsStore.deleteRekord(doomOnIgdb).getOrThrow()
        rekordsStore.deleteRekord(doomOnConsole).getOrThrow()

        assertEquals(expected = listOf(doom, quake), actual = games())
        assertEquals(expected = listOf(doomOnPc, quakeOnPc), actual = plays())
    }

    open fun check_filter_looking_into_an_embedded_class_selects_by_its_fields() = test {
        val ofDoom = Filter.Nested(TestPlayRekord.GAME, Filter.Equals(TestGameKey.ID, "Q1"))
        assertEquals(expected = listOf(doomOnPc, doomOnConsole), actual = plays(ofDoom))

        val onIgdb = Filter.Nested(TestKeyedGameRekord.KEY, Filter.Equals(TestGameKey.ID_TYPE, "igdb"))
        assertEquals(expected = listOf(doomOnIgdb), actual = games(onIgdb))

        // The rekord an embedded class holds is looked into as any rekord held.
        val onPc = Filter.Nested(
            TestKeyedGameRekord.RELEASE,
            Filter.Nested(TestRelease.PLATFORM, Filter.Equals(TestPlatformRekord.TITLE, "PC")),
        )
        assertEquals(expected = listOf(doom), actual = games(onPc))

        // A list of a rekord keyed by an embedded class is linked to it by the fields of the class.
        val portedToPc = Filter.Nested(TestKeyedGameRekord.PORTS, Filter.Equals(TestPlatformRekord.ID, "pc"))
        assertEquals(expected = listOf(doomOnIgdb), actual = games(portedToPc))
    }

    open fun check_filter_naming_the_fields_of_an_embedded_class_selects_by_them() = test {
        assertEquals(expected = listOf(doomOnPc, doomOnConsole), actual = plays(Filter.Equals("game_id", "Q1")))
        assertEquals(expected = listOf(doomOnIgdb), actual = games(Filter.Equals("id_type", "igdb")))
        // A game released holds a date; one that was not holds none, as each field of it does.
        assertEquals(expected = listOf(quake), actual = games(Filter.Equals("release_date", null)))
        assertEquals(
            expected = listOf(doomOnIgdb),
            actual = games(Filter.GreaterThan("release_date", LocalDate(1993, 12, 10))),
        )
    }

    open fun check_rekords_are_ordered_by_the_fields_of_an_embedded_class() = test {
        val byRelease = rekordsStore
            .getRekords<TestKeyedGameRekord>(
                filter = Filter.Nested(TestKeyedGameRekord.KEY, Filter.Equals(TestGameKey.ID, "Q1")),
                orderBy = listOf(Order.Descending("release_date")),
            )
            .getOrThrow()

        assertEquals(expected = listOf(doomOnIgdb, doom), actual = byRelease)
    }
}
