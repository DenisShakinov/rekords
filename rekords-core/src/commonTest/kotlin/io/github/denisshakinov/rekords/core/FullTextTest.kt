@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullTextTest {

    @Test
    fun words_are_runs_of_letters_and_digits_lowered() {
        assertEquals(expected = listOf("super", "mario", "bros"), actual = "Super Mario Bros.".fullTextWords())
        assertEquals(expected = listOf("spider", "man", "2"), actual = "Spider-Man 2".fullTextWords())
        assertEquals(expected = listOf("don", "t", "stop"), actual = "Don't  stop!".fullTextWords())
        assertEquals(expected = listOf("doom2016"), actual = "DOOM2016".fullTextWords())
        assertEquals(expected = emptyList(), actual = "".fullTextWords())
        assertEquals(expected = emptyList(), actual = " -- ... ".fullTextWords())
    }

    @Test
    fun case_of_any_letter_is_ignored_and_diacritics_are_kept() {
        assertEquals(expected = listOf("дум", "вечный"), actual = "ДУМ: Вечный".fullTextWords())
        assertEquals(expected = listOf("époque", "ωμέγα"), actual = "ÉPOQUE ΩΜΈΓΑ".fullTextWords())
        // A letter written with a combining accent stays one word.
        assertEquals(expected = listOf("époque"), actual = "ÉPOQUE".fullTextWords())
    }

    @Test
    fun terms_are_the_words_each_between_spaces() {
        assertEquals(expected = " super mario ", actual = fullTextTerms("Super  Mario!"))
        assertEquals(expected = "  ", actual = fullTextTerms("--"))
        assertEquals(expected = " sup", actual = fullTextSearched("sup"))
    }

    @Test
    fun word_prefix_is_matched_at_the_beginning_of_a_word() {
        assertTrue(hasWordPrefixed("Super Mario", "mar"))
        assertTrue(hasWordPrefixed("Super Mario", "super"))
        assertFalse(hasWordPrefixed("Super Mario", "ario"))
        assertFalse(hasWordPrefixed("Super Mario", "supermario"))
    }

    @Test
    fun full_text_asks_every_word_of_any_field() {
        assertEquals(
            expected = listOf(
                listOf(FieldFilter.WordPrefix("title", "sup"), FieldFilter.WordPrefix("body", "sup")),
                listOf(FieldFilter.WordPrefix("title", "mar"), FieldFilter.WordPrefix("body", "mar")),
            ),
            actual = (Filter.FullText("Sup  MAR sup", "title", "body") as Filter.And).filters.map { word ->
                (word as Filter.Or).filters.toList()
            },
        )
        assertEquals(
            expected = listOf(FieldFilter.WordPrefix("title", "sup")),
            actual = (Filter.FullText("sup", "title") as Filter.And).filters.toList(),
        )
        // No words select every rekord.
        assertEquals(expected = emptyList(), actual = (Filter.FullText(" - ", "title") as Filter.And).filters.toList())
        assertFailsWith<IllegalArgumentException> { Filter.FullText("sup") }
    }

    @Test
    fun word_prefix_is_one_word_lowered() {
        assertEquals(expected = FieldFilter.WordPrefix("title", "mar"), actual = Filter.WordPrefix("title", "Mar"))
        assertFailsWith<IllegalArgumentException> { Filter.WordPrefix("title", "super mario") }
        assertFailsWith<IllegalArgumentException> { Filter.WordPrefix("title", "") }
    }

    @Test
    fun full_text_field_is_declared_on_text_alone() {
        val fields = allFields<FullTextRekord>().associateBy { it.field.name }
        assertTrue(fields.getValue("title").field.fullText)
        // A field of an embedded class keeps the mark under the name it is stored by.
        assertTrue(fields.getValue("credits_author").field.fullText)
        assertFalse(fields.getValue("credits_year").field.fullText)

        assertFailsWith<IllegalArgumentException> { allFields<FullTextNumberRekord>() }
        assertFailsWith<IllegalArgumentException> { allFields<FullTextEncryptedRekord>() }
        assertFailsWith<IllegalArgumentException> { allFields<FullTextEmbeddedRekord>() }
    }
}

@Embedded
private class FullTextCredits(
    @Field(name = "author", fullText = true)
    val author: String,
    @Field(name = "year")
    val year: Int,
)

@Rekord(type = "full_text")
private class FullTextRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "title", fullText = true)
    val title: String?,
    @Field(name = "credits")
    val credits: FullTextCredits,
)

@Rekord(type = "full_text_number")
private class FullTextNumberRekord(
    @Field(name = "id", id = true, fullText = true)
    val id: Long,
)

@Rekord(type = "full_text_encrypted")
private class FullTextEncryptedRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "title", fullText = true, encryption = Encryption.Deterministic)
    val title: String,
)

@Rekord(type = "full_text_embedded")
private class FullTextEmbeddedRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "credits", fullText = true)
    val credits: FullTextCredits,
)
