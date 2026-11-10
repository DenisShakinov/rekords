@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordValues
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.putRekord
import io.github.denisshakinov.rekords.core.putRekords
import io.github.denisshakinov.rekords.core.runOnEditor
import io.github.denisshakinov.rekords.test.TestGeneratedIdRekordsSchema
import io.github.denisshakinov.rekords.test.TestRekordsCipher
import io.github.denisshakinov.rekords.test.TestWriterRekord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** How many times the storage is asked for the largest number while rekords are given theirs. */
class InMemoryGeneratedIdQueriesTest {

    private val editor = QueryCountingEditor(InMemoryRekordsEditor())

    // The store hands the editor the schema, which the rekords are then put by on the editor itself.
    private val rekordsStore = RekordsStore(TestGeneratedIdRekordsSchema(), editor, cipher = TestRekordsCipher())

    @Test
    fun rekords_put_together_read_the_largest_number_once() = runTest {
        rekordsStore.count<TestWriterRekord>().getOrThrow()
        val stored = runOnEditor(editor) {
            putRekords(List(5) { TestWriterRekord(name = "Writer $it") })
        }

        assertEquals(expected = (1L..5L).toList(), actual = stored.map { it.id })
        assertEquals(expected = 1, actual = editor.queries)
    }

    @Test
    fun rekords_put_one_by_one_read_the_largest_number_each() = runTest {
        rekordsStore.count<TestWriterRekord>().getOrThrow()
        val stored = runOnEditor(editor) {
            List(3) { putRekord(TestWriterRekord(name = "Writer $it")) }
        }

        assertEquals(expected = listOf(1L, 2L, 3L), actual = stored.map { it.id })
        assertEquals(expected = 3, actual = editor.queries)
    }
}

/** Counts the queries run on [editor] through it. */
private class QueryCountingEditor(private val editor: RekordsEditor) : RekordsEditor by editor {

    var queries: Int = 0
        private set

    override suspend fun query(
        rekordType: String,
        fields: List<String>?,
        filter: Filter?,
        orderBy: List<Order>?,
        limit: Int?,
        offset: Int?,
        distinctBy: List<String>?,
    ): List<RekordValues> {
        queries++
        return runOnEditor(editor) { query(rekordType, fields, filter, orderBy, limit, offset, distinctBy) }
    }
}
