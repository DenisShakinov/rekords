package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.delete
import io.github.denisshakinov.rekords.core.plus
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals

/**
 * Runs a store of rekords keyed by rekords they hold over [rekordsEditor]: a session keyed by its
 * game and its platform, a run keyed by its session - so two levels deep - and a list of sessions.
 * Rekords differing in no more than the rekord held as an id are to be kept apart, and one written
 * again under the same rekord held is to be updated.
 */
abstract class RekordsNestedIdTest(private val rekordsEditor: RekordsEditor) {

    private val rekordsStore = RekordsStore(TestNestedIdRekordsSchema(), rekordsEditor)

    private val pc = TestPlatformRekord(id = "pc", kind = 1, title = "PC")
    // The same id as the PC, of another kind: only both ids together tell the two apart.
    private val pcHandheld = TestPlatformRekord(id = "pc", kind = 2, title = "PC handheld")
    private val console = TestPlatformRekord(id = "ps5", kind = 1, title = "PlayStation 5")

    private val onPc = TestSessionRekord(gameId = 1, platform = pc, note = "on PC")
    private val onHandheld = TestSessionRekord(gameId = 1, platform = pcHandheld, note = "on the handheld")
    private val onConsole = TestSessionRekord(gameId = 1, platform = console, note = "on the console")

    private fun test(block: suspend () -> Unit) = runTest {
        rekordsStore.transaction {
            delete<TestLibraryRekord>()
            delete<TestRunRekord>()
            delete<TestSessionRekord>()
            delete<TestPlatformRekord>()
        }.getOrThrow()
        rekordsStore.putRekords(listOf(onPc, onHandheld, onConsole)).getOrThrow()
        block()
    }

    private suspend fun sessions(filter: Filter? = null): List<TestSessionRekord> = rekordsStore
        .getRekords<TestSessionRekord>(filter, orderBy = listOf(Order.Ascending(TestSessionRekord.NOTE)))
        .getOrThrow()

    open fun check_rekords_differing_in_the_rekord_held_as_an_id_are_kept_apart() = test {
        assertEquals(expected = listOf(onPc, onConsole, onHandheld), actual = sessions())
    }

    open fun check_rekord_written_again_under_the_same_rekord_held_is_updated() = test {
        val replayed = onPc.copy(note = "replayed on PC")
        rekordsStore.putRekord(replayed).getOrThrow()

        assertEquals(expected = listOf(onConsole, onHandheld, replayed), actual = sessions())
    }

    open fun check_rekord_is_deleted_by_the_rekord_held_as_an_id() = test {
        rekordsStore.deleteRekord(onHandheld).getOrThrow()

        assertEquals(expected = listOf(onPc, onConsole), actual = sessions())
    }

    open fun check_rekord_held_as_an_id_updated_is_read_in_the_rekords_holding_it() = test {
        val renamed = pc.copy(title = "Personal computer")
        rekordsStore.putRekord(renamed).getOrThrow()

        val session = sessions(Filter.Nested(TestSessionRekord.PLATFORM, Filter.Equals(TestPlatformRekord.KIND, 1)) +
            Filter.Nested(TestSessionRekord.PLATFORM, Filter.Equals(TestPlatformRekord.ID, "pc")))
        assertEquals(expected = listOf(onPc.copy(platform = renamed)), actual = session)
        assertEquals(expected = 3, actual = rekordsStore.count<TestSessionRekord>().getOrThrow())
    }

    open fun check_filter_on_the_rekord_held_as_an_id_selects_the_rekords_holding_it() = test {
        val onPlayStation = sessions(Filter.Nested(TestSessionRekord.PLATFORM, Filter.Equals(TestPlatformRekord.TITLE, "PlayStation 5")))

        assertEquals(expected = listOf(onConsole), actual = onPlayStation)
    }

    open fun check_rekords_keyed_by_rekords_keyed_by_rekords_are_kept_apart() = test {
        val firstOnPc = TestRunRekord(session = onPc, number = 1, hours = 10)
        val firstOnHandheld = TestRunRekord(session = onHandheld, number = 1, hours = 20)
        val secondOnPc = TestRunRekord(session = onPc, number = 2, hours = 30)
        rekordsStore.putRekords(listOf(firstOnPc, firstOnHandheld, secondOnPc)).getOrThrow()
        val longerFirstOnPc = firstOnPc.copy(hours = 15)
        rekordsStore.putRekord(longerFirstOnPc).getOrThrow()

        val runs = rekordsStore
            .getRekords<TestRunRekord>(orderBy = listOf(Order.Ascending(TestRunRekord.HOURS)))
            .getOrThrow()
        assertEquals(expected = listOf(longerFirstOnPc, firstOnHandheld, secondOnPc), actual = runs)

        rekordsStore.deleteRekord(firstOnHandheld).getOrThrow()
        assertEquals(expected = 2, actual = rekordsStore.count<TestRunRekord>().getOrThrow())
    }

    open fun check_list_of_rekords_keyed_by_rekords_is_read_back_and_replaced() = test {
        val library = TestLibraryRekord(id = 1, sessions = listOf(onHandheld, onPc))
        rekordsStore.putRekord(library).getOrThrow()
        assertEquals(expected = library, actual = rekordsStore.getRekord<TestLibraryRekord>(Filter.Equals(TestLibraryRekord.ID, 1L)).getOrThrow())

        val trimmed = library.copy(sessions = listOf(onConsole, onPc))
        rekordsStore.putRekord(trimmed).getOrThrow()
        assertEquals(expected = trimmed, actual = rekordsStore.getRekord<TestLibraryRekord>(Filter.Equals(TestLibraryRekord.ID, 1L)).getOrThrow())

        val withHandheld = Filter.Nested(TestLibraryRekord.SESSIONS, Filter.Equals(TestSessionRekord.NOTE, "on the handheld"))
        assertEquals(expected = 0, actual = rekordsStore.count<TestLibraryRekord>(withHandheld).getOrThrow())
    }
}
