@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.FieldFilter
import io.github.denisshakinov.rekords.core.FieldValueFilter
import io.github.denisshakinov.rekords.core.FieldWithType
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.RekordKType
import io.github.denisshakinov.rekords.core.RekordListKType
import io.github.denisshakinov.rekords.core.elementRekordType
import io.github.denisshakinov.rekords.core.field
import io.github.denisshakinov.rekords.core.rekordType
import io.github.denisshakinov.rekords.core.type

/** A condition of a WHERE clause, and the values its placeholders stand for, in their order. */
internal class SQLCondition(val sql: String, val args: List<PrimitiveRekordValue?>)

/**
 * The condition [filter] makes of the rows of [tableName], which a table of no rekord type - the
 * one the schema version is kept in - is filtered with.
 */
internal fun Filter.toSQLCondition(tableName: String): SQLCondition =
    SQLConditionBuilder(schemaOf = { emptyMap() }).build(this, tableName, tableName)

/**
 * Makes conditions of filters on the rows of a rekord type, the fields of each rekord type being
 * the ones [schemaOf] gives.
 *
 * A filter on a nested rekord, or on the rekords of a list, is a correlated EXISTS over the table
 * they are kept in - and over the junction table, for a list - rather than a join: a rekord is
 * selected once however many of its nested ones match, the rows read are the rekord's own, so a
 * limit counts rekords, and the lists read back are whole rather than cut down to what matched.
 * The filters within it are made the same way, so nested rekords are looked into at any depth.
 *
 * The tables an EXISTS reads are aliased each with a name of its own, so that one rekord type
 * nested in several fields, or within itself, is read in each place apart.
 */
internal class SQLConditionBuilder(
    private val schemaOf: (rekordType: String) -> Map<String, FieldWithType>,
) {

    private var aliasCount = 0

    /** The condition [filter] makes of the rows of [rekordType], reached as [alias]. */
    fun build(filter: Filter, rekordType: String, alias: String): SQLCondition {
        val args = mutableListOf<PrimitiveRekordValue?>()
        return SQLCondition(condition(filter, rekordType, alias, args), args)
    }

    private fun condition(
        filter: Filter,
        rekordType: String,
        alias: String,
        args: MutableList<PrimitiveRekordValue?>,
    ): String = when (filter) {
        is Filter.And -> filter.filters.joined(" AND ", whenNone = "1") { condition(it, rekordType, alias, args) }
        is Filter.Or -> filter.filters.joined(" OR ", whenNone = "0") { condition(it, rekordType, alias, args) }
        is Filter.Not -> "(NOT ${condition(filter.filter, rekordType, alias, args)})"
        is FieldFilter.Nested -> nested(filter, rekordType, alias, args)
        is FieldFilter.InList -> if (filter.list.isEmpty()) {
            "(0)"
        } else {
            args += filter.list
            "(${column(alias, filter.field)} IN (${filter.list.joinToString(",") { "?" }}))"
        }
        is FieldFilter.Contains -> {
            args += "%${filter.value}%"
            "(${column(alias, filter.field)} LIKE ?)"
        }
        is FieldValueFilter.Equals -> if (filter.value == null) {
            "(${column(alias, filter.field)} IS NULL)"
        } else {
            comparison(alias, filter, "=", args)
        }
        is FieldValueFilter.LessThan -> comparison(alias, filter, "<", args)
        is FieldValueFilter.LessThanOrEquals -> comparison(alias, filter, "<=", args)
        is FieldValueFilter.GreaterThan -> comparison(alias, filter, ">", args)
        is FieldValueFilter.GreaterThanOrEquals -> comparison(alias, filter, ">=", args)
    }

    private fun comparison(
        alias: String,
        filter: FieldValueFilter,
        operator: String,
        args: MutableList<PrimitiveRekordValue?>,
    ): String {
        args += filter.value
        return "(${column(alias, filter.field)} $operator ?)"
    }

    private fun nested(
        filter: FieldFilter.Nested,
        rekordType: String,
        alias: String,
        args: MutableList<PrimitiveRekordValue?>,
    ): String = when (val type = schemaOf(rekordType)[filter.field]?.type) {
        is RekordKType -> {
            val childType = type.rekordType()
            val child = nextAlias()
            val link = idFieldNames(childType).joinToString(" AND ") { id ->
                "$child.$id = $alias.${filter.field}_$id"
            }
            val inner = condition(filter.filter, childType, child, args)
            "(EXISTS (SELECT 1 FROM $childType AS $child WHERE ${link.ifEmpty { "1" }} AND $inner))"
        }
        is RekordListKType -> {
            val childType = type.elementRekordType()
            val junction = nextAlias()
            val child = nextAlias()
            val toChild = idFieldNames(childType).joinToString(" AND ") { id ->
                "$child.$id = $junction.${childType}_$id"
            }
            val toParent = idFieldNames(rekordType).joinToString(" AND ") { id ->
                "$junction.${rekordType}_$id = $alias.$id"
            }
            val inner = condition(filter.filter, childType, child, args)
            "(EXISTS (SELECT 1 FROM ${junctionTable(rekordType, filter.field)} AS $junction " +
                "JOIN $childType AS $child ON ${toChild.ifEmpty { "1" }} " +
                "WHERE ${toParent.ifEmpty { "1" }} AND $inner))"
        }
        // A field the schema holds no rekord in is filtered as the rekord's own.
        else -> condition(filter.filter, rekordType, alias, args)
    }

    /** The fields identifying a rekord of [rekordType], which what refers to it is made of. */
    fun idFieldNames(rekordType: String): List<String> = schemaOf(rekordType).values
        .filter { it.field.id && it.type !is RekordKType && it.type !is RekordListKType }
        .map { it.field.name }

    private fun nextAlias(): String = "_r${++aliasCount}"

    private fun column(alias: String, field: String): String = if ("." in field) field else "$alias.$field"

    private inline fun Iterable<Filter>.joined(
        separator: String,
        whenNone: String,
        condition: (Filter) -> String,
    ): String {
        val conditions = map(condition)
        return if (conditions.isEmpty()) "($whenNone)" else conditions.joinToString(separator, "(", ")")
    }
}

/** The table the rekords of the list [field] of [rekordType] are linked to theirs in. */
internal fun junctionTable(rekordType: String, field: String): String = "${rekordType}_$field"
