@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

/**
 * The words of this text as a field marked [Field.fullText] is searched by: each a run of letters,
 * digits and the marks combining with them, lowered - everything else parting them. The case of
 * any letter is told, Cyrillic or Greek as much as Latin.
 *
 * A character beyond the Basic Multilingual Plane is kept in a word. Nothing else is folded: a
 * letter with a diacritic is not the letter without it, and a text of no spaces - Chinese, say -
 * is one word.
 */
@InternalRekordsApi
fun String.fullTextWords(): List<String> {
    val words = mutableListOf<String>()
    val word = StringBuilder()
    for (char in this) {
        if (char.isWordChar()) {
            word.append(char)
        } else if (word.isNotEmpty()) {
            words += word.toString().lowercase()
            word.clear()
        }
    }
    if (word.isNotEmpty()) words += word.toString().lowercase()
    return words
}

/**
 * How a storage that cannot split text keeps the words of [text]: each one preceded and followed
 * by a space, so that a word beginning with what is searched for is the search after a space.
 */
@InternalRekordsApi
fun fullTextTerms(text: String): String = text.fullTextWords().joinToString(" ", prefix = " ", postfix = " ")

/**
 * The text a storage that cannot split text searches its kept words for: [prefix] after a space.
 * See [fullTextTerms].
 */
@InternalRekordsApi
fun fullTextSearched(prefix: String): String = " $prefix"

private fun Char.isWordChar(): Boolean = isLetterOrDigit() || when (category) {
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
    CharCategory.SURROGATE -> true
    else -> false
}

/** Whether the words of [text] hold one beginning with [prefix] - see [FieldFilter.WordPrefix]. */
internal fun hasWordPrefixed(text: String, prefix: String): Boolean =
    text.fullTextWords().any { it.startsWith(prefix) }

/**
 * This filter, once each field it looks for words in has been found to be [Field.fullText] in
 * [rekordType] - whose fields, and those of the rekords it holds, [schemaOf] gives. A storage keeps
 * the words of those fields alone, and every one is to fail the same on any other.
 *
 * @throws IllegalArgumentException when a [FieldFilter.WordPrefix] names a field that is not.
 */
internal fun Filter.checkFullText(
    rekordType: String,
    schemaOf: (rekordType: String) -> Map<String, FieldWithType>,
): Filter = apply {
    when (this) {
        is Filter.And -> filters.forEach { it.checkFullText(rekordType, schemaOf) }
        is Filter.Or -> filters.forEach { it.checkFullText(rekordType, schemaOf) }
        is Filter.Not -> filter.checkFullText(rekordType, schemaOf)
        is FieldFilter.Nested -> {
            val nestedType = when (val type = schemaOf(rekordType)[field]?.type) {
                is RekordKType -> type.rekordType()
                is RekordListKType -> type.elementRekordType()
                else -> rekordType
            }
            filter.checkFullText(nestedType, schemaOf)
        }
        is FieldFilter.WordPrefix -> require(schemaOf(rekordType)[field]?.field?.fullText == true) {
            "$field of $rekordType is not fullText, so its words are not kept to be found by: " +
                "mark it @Field(fullText = true)."
        }
        else -> Unit
    }
}
