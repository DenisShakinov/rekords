@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

/**
 * Which rekords an operation selects - the same ones in every engine.
 *
 * A field holding null is selected by [Equals] to null, and by [InList] holding null, alone:
 * nothing is less or more than null, it contains nothing and matches no pattern. [Not] selects
 * every rekord the filter it holds does not, those holding null included.
 */
sealed interface Filter {
    companion object {
        fun Equals(field: String, value: PrimitiveRekordValue?) =
            FieldValueFilter.Equals(field, value.encode())

        fun LessThan(field: String, value: PrimitiveRekordValue?) =
            FieldValueFilter.LessThan(field, value.encode())

        fun LessThanOrEquals(field: String, value: PrimitiveRekordValue?) =
            FieldValueFilter.LessThanOrEquals(field, value.encode())

        fun GreaterThan(field: String, value: PrimitiveRekordValue?) =
            FieldValueFilter.GreaterThan(field, value.encode())

        fun GreaterThanOrEquals(field: String, value: PrimitiveRekordValue?) =
            FieldValueFilter.GreaterThanOrEquals(field, value.encode())

        fun InList(field: String, list: List<PrimitiveRekordValue?>) =
            FieldFilter.InList(field, list.map { it.encode() })

        /**
         * Selects the rekords whose [field] holds [value] as a part of its text. Every character is
         * looked for as itself - `%` and `_` included - and an empty value is a part of any text.
         * A field holding no text is looked into as the text of its value.
         *
         * Case counts unless [ignoreCase] is set, and then for the Latin letters `A`-`Z` alone:
         * SQLite tells no other letter's case without ICU, which neither the SQLite bundled with
         * `androidx.sqlite` nor the one Apple platforms ship is built with, so no engine does -
         * `"doom"` finds `"Doom"`, while `"дум"` does not find `"Дум"`.
         */
        fun Contains(field: String, value: String, ignoreCase: Boolean = false) =
            FieldFilter.Contains(field, value, ignoreCase)

        /**
         * Selects the rekords whose [field] matches [pattern] the way SQLite's `GLOB` matches it,
         * in every engine alike:
         *
         * - `*` matches any run of characters, none included, and `?` exactly one;
         * - `[abc]` matches one of the characters listed, `[a-z]` one within the range, and
         *   `[^...]` one the set does not hold. A `]` right after the bracket - or after the
         *   caret - is one of the characters, as is a `-` listed first or last.
         * - Nothing escapes a character: `*`, `?` and `[` stand for themselves inside a set,
         *   `[*]` matching an asterisk. A set left open matches nothing.
         *
         * Case counts unless [ignoreCase] is set, and then for the Latin letters `A`-`Z` alone, as
         * [Contains] ignores it: the text and the pattern are both lowered before they are
         * matched, the ranges of a set included - `[A-Z]` takes in `a`-`z` as well, and a range
         * from an upper case letter to a lower case one, `[A-z]`, is read as the lowered `[a-z]`.
         *
         * A field holding no text is matched as the text of its value; a null one matches no
         * pattern.
         */
        fun Glob(field: String, pattern: String, ignoreCase: Boolean = false) =
            FieldFilter.Glob(field, pattern, ignoreCase)

        fun Nested(field: String, filter: FieldFilter) = FieldFilter.Nested(field, filter)

        fun And(vararg filters: Filter) = And(filters.asIterable())

        fun Or(vararg filters: Filter) = Or(filters.asIterable())
    }

    class Not(val filter: FieldFilter) : Filter
    class And(val filters: Iterable<Filter>) : Filter
    class Or(val filters: Iterable<Filter>) : Filter
}

sealed class FieldFilter(open val field: String) : Filter {
    data class InList internal constructor(
        override val field: String,
        val list: List<PrimitiveRekordValue?>
    ) : FieldFilter(field)

    /** See [Filter.Contains]. */
    data class Contains internal constructor(
        override val field: String,
        val value: String,
        val ignoreCase: Boolean = false,
    ) : FieldFilter(field)

    /** See [Filter.Glob]. */
    data class Glob internal constructor(
        override val field: String,
        val pattern: String,
        val ignoreCase: Boolean = false,
    ) : FieldFilter(field)

    data class Nested internal constructor(
        override val field: String,
        val filter: FieldFilter,
    ) : FieldFilter(field)
}

sealed class FieldValueFilter(field: String, open val value: PrimitiveRekordValue?) :
    FieldFilter(field) {
    data class Equals internal constructor(
        override val field: String,
        override val value: PrimitiveRekordValue?
    ) : FieldValueFilter(field, value)

    data class LessThan internal constructor(
        override val field: String,
        override val value: PrimitiveRekordValue?
    ) : FieldValueFilter(field, value)

    data class LessThanOrEquals internal constructor(
        override val field: String,
        override val value: PrimitiveRekordValue?
    ) : FieldValueFilter(field, value)

    data class GreaterThan internal constructor(
        override val field: String,
        override val value: PrimitiveRekordValue?
    ) : FieldValueFilter(field, value)

    data class GreaterThanOrEquals internal constructor(
        override val field: String,
        override val value: PrimitiveRekordValue?
    ) : FieldValueFilter(field, value)
}

operator fun Filter.plus(filter: Filter?): Filter {
    if (filter != null) {
        return Filter.And(this, filter)
    }
    return this
}

infix fun Filter.or(filter: Filter?): Filter {
    if (filter != null) {
        return Filter.Or(this, filter)
    }
    return this
}