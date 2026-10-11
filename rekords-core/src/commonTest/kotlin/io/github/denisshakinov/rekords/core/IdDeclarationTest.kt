@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdDeclarationTest {

    private val rekord = NestedIdRekord(id = 1, inner = InnerRekord(id = 2, kind = "a", title = "Inner"))

    @Test
    fun id_holding_a_rekord_is_reached_through_the_ids_of_that_rekord() {
        assertEquals(
            expected = listOf(listOf("id"), listOf("inner", "id"), listOf("inner", "kind")),
            actual = allFields<NestedIdRekord>().idPaths(),
        )
        assertEquals(expected = "a", actual = encode(rekord).primitiveAt(listOf("inner", "kind")))
    }

    @Test
    fun id_holding_a_rekord_selects_by_the_ids_of_that_rekord() {
        val filter: Filter = idsFilter(encode(rekord), allFields<NestedIdRekord>())

        assertTrue(encode(rekord).matches(filter))
        // Another title is the same rekord; another kind of the rekord held is another one.
        val retitled = NestedIdRekord(id = 1, inner = InnerRekord(id = 2, kind = "a", title = "Other"))
        assertTrue(encode(retitled).matches(filter))
        val otherKind = NestedIdRekord(id = 1, inner = InnerRekord(id = 2, kind = "b", title = "Inner"))
        assertFalse(encode(otherKind).matches(filter))
    }

    @Test
    fun id_holding_a_rekord_that_may_be_none_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<NullableNestedIdRekord>() }
    }

    @Test
    fun id_holding_a_list_of_rekords_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<NestedListIdRekord>() }
    }
}

@Rekord(type = "id_inner")
private class InnerRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "kind", id = true)
    val kind: String,
    @Field(name = "title")
    val title: String,
)

@Rekord(type = "nested_id")
private class NestedIdRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "inner", id = true)
    val inner: InnerRekord,
)

@Rekord(type = "nullable_nested_id")
private class NullableNestedIdRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "inner", id = true)
    val inner: InnerRekord?,
)

@Rekord(type = "nested_list_id")
private class NestedListIdRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "inners", id = true)
    val inners: List<InnerRekord>,
)
