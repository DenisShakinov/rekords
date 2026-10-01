package io.github.denisshakinov.rekords.indexeddb

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.test.TestGamePropertyRekord
import io.github.denisshakinov.rekords.test.TestGameRekord
import io.github.denisshakinov.rekords.test.TestGameStateRekord
import io.github.denisshakinov.rekords.test.TestRekordsSchema
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** What IndexedDB keeps that no other storage has to: a JavaScript number is a Double. */
class IndexedDBNumberTest {

    private val rekordsEditor: RekordsEditor = IndexedDBRekordsEditor(nextNumberDatabaseName())

    /** A [Long] a JavaScript number cannot hold exactly is kept exactly all the same. */
    @Test
    fun long_beyond_what_a_number_holds_is_kept_exactly() = runTest {
        val store = RekordsStore(TestRekordsSchema(), rekordsEditor)
        store.putRekord(doom(steamId = Long.MAX_VALUE)).getOrThrow()

        val doom = store.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrThrow()
        assertEquals(expected = Long.MAX_VALUE, actual = doom?.steamId)
    }

    private fun doom(steamId: Long? = null) = TestGameRekord(
        id = 1,
        title = "Doom",
        releaseDate = LocalDate(1993, 12, 10),
        remasterDate = LocalDate(2019, 7, 26),
        steamId = steamId,
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
}

private var numberDatabaseCount = 0

private fun nextNumberDatabaseName(): String {
    installFakeIndexedDB()
    return "rekords-number-test-${numberDatabaseCount++}"
}
