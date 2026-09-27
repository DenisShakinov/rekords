@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

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

        fun Contains(field: String, value: String) = FieldFilter.Contains(field, value)

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

    data class Contains internal constructor(
        override val field: String,
        val value: String
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