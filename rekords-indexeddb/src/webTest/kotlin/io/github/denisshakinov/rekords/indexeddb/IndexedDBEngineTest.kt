package io.github.denisshakinov.rekords.indexeddb

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.test.TestGameRekord
import io.github.denisshakinov.rekords.test.TestGameStateRekord
import io.github.denisshakinov.rekords.test.TestRekordsSchema
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** A web target depending on this module has its store created with IndexedDB, without naming it. */
class IndexedDBEngineTest {

    @Test
    fun store_created_without_an_engine_keeps_its_rekords_in_indexeddb() = runTest {
        installFakeIndexedDB()
        val store = RekordsStore(TestRekordsSchema()) { name = "rekords-engine-test" }

        store.putRekord(
            TestGameRekord(
                id = 1,
                title = "Doom",
                releaseDate = LocalDate(1993, 12, 10),
                state = TestGameStateRekord(id = 3, name = "Completed"),
                properties = emptyList(),
            )
        ).getOrThrow()

        val doom = store.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrThrow()
        assertEquals(expected = "Doom", actual = doom?.title)
    }
}
