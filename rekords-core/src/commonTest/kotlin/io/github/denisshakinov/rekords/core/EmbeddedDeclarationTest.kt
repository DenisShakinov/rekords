@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmbeddedDeclarationTest {

    @Test
    fun fields_of_an_embedded_class_are_fields_of_the_rekord_holding_it() {
        val fields = allFields<EmbeddingRekord>().associate { (field, type) -> field.name to (field.id to type.isMarkedNullable) }

        assertEquals(
            expected = mapOf(
                // Not prefixed: the class's own names.
                "id" to (true to false),
                "id_type" to (true to false),
                // Prefixed, nested two levels deep, and nullable with the field holding them.
                "release_date" to (false to true),
                "release_place_country" to (false to true),
                "release_place_city" to (false to true),
                "title" to (false to false),
            ),
            actual = fields,
        )
    }

    @Test
    fun embedded_class_is_written_as_the_rekord_s_fields_and_read_back() {
        val rekord = EmbeddingRekord(
            id = Key(id = "Q1", idType = "wikidata"),
            release = Release(date = LocalDate(1993, 12, 10), place = Place(country = "us", city = null)),
            title = "Doom",
        )

        val values: RekordValues = encode(rekord)

        assertEquals(
            expected = setOf("id", "id_type", "release_date", "release_place_country", "release_place_city", "title"),
            actual = values.keys,
        )
        assertEquals(expected = rekord, actual = values.decode<EmbeddingRekord>())
    }

    @Test
    fun embedded_class_that_is_none_is_written_as_fields_holding_none_and_read_back_as_none() {
        val rekord = EmbeddingRekord(id = Key(id = "Q2", idType = "wikidata"), release = null, title = "Quake")

        val values: RekordValues = encode(rekord)

        assertEquals(expected = null, actual = values["release_date"])
        assertEquals(expected = true, actual = values.containsKey("release_place_country"))
        assertEquals(expected = rekord, actual = values.decode<EmbeddingRekord>())
    }

    @Test
    fun embedded_class_that_may_be_none_without_a_field_that_cannot_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<NoneTellingRekord>() }
    }

    @Test
    fun embedded_id_that_may_be_none_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<NullableEmbeddedIdRekord>() }
    }

    @Test
    fun embedded_class_holding_a_list_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<ListEmbeddingRekord>() }
    }
}

@Embedded
private data class Key(
    @Field(name = "id")
    val id: String,
    @Field(name = "id_type")
    val idType: String,
)

@Embedded
private data class Place(
    @Field(name = "country")
    val country: String,
    @Field(name = "city")
    val city: String?,
)

@Embedded
private data class Release(
    @Field(name = "date")
    val date: LocalDate,
    @Field(name = "place")
    val place: Place,
)

@Rekord(type = "embedding")
private data class EmbeddingRekord(
    @Field(name = "id", id = true, prefixed = false)
    val id: Key,
    @Field(name = "release")
    val release: Release?,
    @Field(name = "title")
    val title: String,
)

@Embedded
private data class AllOptional(
    @Field(name = "note")
    val note: String?,
)

@Rekord(type = "none_telling")
private class NoneTellingRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "optional")
    val optional: AllOptional?,
)

@Rekord(type = "nullable_embedded_id")
private class NullableEmbeddedIdRekord(
    @Field(name = "key", id = true)
    val key: Key?,
)

@Rekord(type = "listed_inner")
private class ListedInner(
    @Field(name = "id", id = true)
    val id: Long,
)

@Embedded
private class Listing(
    @Field(name = "items")
    val items: List<ListedInner>,
)

@Rekord(type = "list_embedding")
private class ListEmbeddingRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "listing")
    val listing: Listing,
)
