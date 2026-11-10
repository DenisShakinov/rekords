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

class GeneratedIdDeclarationTest {

    @Test
    fun generated_int_long_and_string_ids_are_accepted() {
        assertTrue(allFields<GeneratedLongRekord>().single { it.field.id }.field.generated)
        assertTrue(allFields<GeneratedIntRekord>().single { it.field.id }.field.generated)
        assertTrue(allFields<GeneratedEncryptedStringRekord>().single { it.field.id }.field.generated)
    }

    @Test
    fun generated_field_that_is_not_an_id_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<GeneratedNotIdRekord>() }
    }

    @Test
    fun generated_id_of_another_type_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<GeneratedDoubleRekord>() }
    }

    @Test
    fun generated_id_that_may_be_none_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<GeneratedNullableRekord>() }
    }

    @Test
    fun generated_number_encrypted_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<GeneratedEncryptedLongRekord>() }
    }

    @Test
    fun generated_id_among_other_ids_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<GeneratedAmongIdsRekord>() }
    }

    @Test
    fun generated_field_of_an_embedded_class_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<GeneratedEmbeddingRekord>() }
    }
}

@Rekord(type = "generated_long")
private class GeneratedLongRekord(
    @Field(name = "id", id = true, generated = true)
    val id: Long = 0,
)

@Rekord(type = "generated_int")
private class GeneratedIntRekord(
    @Field(name = "id", id = true, generated = true)
    val id: Int = 0,
)

@Rekord(type = "generated_encrypted_string")
private class GeneratedEncryptedStringRekord(
    @Field(name = "id", id = true, generated = true, encryption = Encryption.Deterministic)
    val id: String = "",
)

@Rekord(type = "generated_not_id")
private class GeneratedNotIdRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "number", generated = true)
    val number: Long = 0,
)

@Rekord(type = "generated_double")
private class GeneratedDoubleRekord(
    @Field(name = "id", id = true, generated = true)
    val id: Double = 0.0,
)

@Rekord(type = "generated_nullable")
private class GeneratedNullableRekord(
    @Field(name = "id", id = true, generated = true)
    val id: Long? = null,
)

@Rekord(type = "generated_encrypted_long")
private class GeneratedEncryptedLongRekord(
    @Field(name = "id", id = true, generated = true, encryption = Encryption.Deterministic)
    val id: Long = 0,
)

@Rekord(type = "generated_among_ids")
private class GeneratedAmongIdsRekord(
    @Field(name = "id", id = true, generated = true)
    val id: Long = 0,
    @Field(name = "kind", id = true)
    val kind: String,
)

@Embedded
private class GeneratedKey(
    @Field(name = "id", id = true, generated = true)
    val id: Long = 0,
)

@Rekord(type = "generated_embedding")
private class GeneratedEmbeddingRekord(
    @Field(name = "key", id = true)
    val key: GeneratedKey,
)
