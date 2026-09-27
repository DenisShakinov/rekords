@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

/**
 * Reads what the filter holds a field to: the one value of an equality, the several of a list.
 *
 * Only an `And` is followed, and nothing below an `Or` or a `Not` counts - there a value a field
 * is compared to no longer tells which rekords the filter can select. Two clauses on one field
 * leave the last read, which selects no fewer rekords than both together do.
 */
@InternalRekordsApi
fun Filter.pinnedValues(): Map<String, List<PrimitiveRekordValue?>> =
    mutableMapOf<String, List<PrimitiveRekordValue?>>().also { collectPinnedValues(it) }

private fun Filter.collectPinnedValues(into: MutableMap<String, List<PrimitiveRekordValue?>>) {
    when (this) {
        is FieldValueFilter.Equals -> into[field] = listOf(value)
        is FieldFilter.InList -> into[field] = list
        is Filter.And -> filters.forEach { it.collectPinnedValues(into) }
        else -> Unit
    }
}

/**
 * Whether the rekord [RekordValues] hold is one [filter] selects - how an editor that reads its
 * rekords into memory to filter them tells, the same for every one of them.
 */
@InternalRekordsApi
fun RekordValues.matches(filter: Filter): Boolean {
    return when (filter) {
        is FieldValueFilter.Equals -> (this[filter.field] as? RekordValue.Primitive)?.value == filter.value
        is FieldValueFilter.LessThan -> comparePrimitive(this[filter.field], filter.value) < 0
        is FieldValueFilter.LessThanOrEquals -> comparePrimitive(
            this[filter.field],
            filter.value
        ) <= 0
        is FieldValueFilter.GreaterThan -> comparePrimitive(this[filter.field], filter.value) > 0
        is FieldValueFilter.GreaterThanOrEquals -> comparePrimitive(
            this[filter.field],
            filter.value
        ) >= 0
        is FieldFilter.InList -> filter.list.contains((this[filter.field] as? RekordValue.Primitive)?.value)
        is FieldFilter.Contains -> (this[filter.field] as? RekordValue.Primitive)?.value?.toString()
            ?.contains(filter.value) == true
        is FieldFilter.Nested -> when (val composite = this[filter.field]) {
            is RekordValue.Composite -> composite.values.matches(filter.filter)
            is RekordValue.CompositeList -> composite.list.any { it.values.matches(filter.filter) }
            else -> false
        }
        is Filter.Not -> !matches(filter.filter)
        is Filter.And -> filter.filters.all { matches(it) }
        is Filter.Or -> filter.filters.any { matches(it) }
    }
}

/** How the rekords [RekordValues] hold and [other] holds are ordered by [orderBy]. */
@InternalRekordsApi
fun RekordValues.compareTo(other: RekordValues, orderBy: List<Order>): Int {
    var result = 0
    for (order: Order in orderBy) {
        if (result != 0) {
            break
        }
        result = when (order) {
            is Order.Ascending -> comparePrimitive(
                this[order.field],
                (other[order.field] as? RekordValue.Primitive)?.value
            )
            is Order.Descending -> comparePrimitive(
                other[order.field],
                (this[order.field] as? RekordValue.Primitive)?.value
            )
        }
    }
    return result
}

@Suppress("UNCHECKED_CAST")
private fun comparePrimitive(left: RekordValue?, right: PrimitiveRekordValue?): Int {
    return (left as? RekordValue.Primitive).compareTo(right?.asRekordValue())
}
