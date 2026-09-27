package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.FieldFilter
import io.github.denisshakinov.rekords.core.FieldValueFilter
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.PrimitiveRekordValues
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface SQLConnection : AutoCloseable {

    suspend fun rawQuery(
        query: String,
        args: Iterable<PrimitiveRekordValue?>? = null
    ): Iterable<PrimitiveRekordValues>

    /**
     * Returns the schema of the given table as a list of column descriptors.
     * Used for SQL-agnostic table recreation during schema migrations.
     *
     * @param tableName the name of the table to inspect.
     * @return the list of [SQLColumn] describing each column.
     */
    suspend fun tableSchema(tableName: String): List<SQLColumn>
}

private const val listSeparator = ","

internal data class SqlJoin(val table: String, val onCondition: String)

internal suspend fun SQLConnection.select(
    tableName: String,
    columnNames: List<String>? = null,
    joins: List<SqlJoin> = emptyList(),
    filter: Filter? = null,
    orderBy: List<Order>? = null,
    limit: Int? = null,
    offset: Int? = null,
): Iterable<PrimitiveRekordValues> {
    val queryBuilder = StringBuilder()
    queryBuilder.append("SELECT ")
    queryBuilder.append(columnNames?.joinToString(listSeparator) ?: "*")

    // When JOINs expand rows (one parent → many rows), LIMIT must be applied before joining.
    // Wrap the main table in a subquery so LIMIT/OFFSET operate on parent rows only.
    val needsSubquery = joins.isNotEmpty() && (limit != null || offset != null)
    if (needsSubquery) {
        queryBuilder.append(" FROM (SELECT * FROM $tableName")
        if (filter != null) queryBuilder.append(" WHERE ${filter.toSqlWhereString()}")
        if (!orderBy.isNullOrEmpty()) queryBuilder.append(" ORDER BY ${orderBy.toSqlOrderByString()}")
        if (limit != null) queryBuilder.append(" LIMIT $limit")
        if (offset != null) queryBuilder.append(" OFFSET $offset")
        queryBuilder.append(") AS $tableName")
        for (join in joins) queryBuilder.append(" LEFT JOIN ${join.table} ON ${join.onCondition}")
    } else {
        queryBuilder.append(" FROM $tableName")
        for (join in joins) queryBuilder.append(" LEFT JOIN ${join.table} ON ${join.onCondition}")
        if (filter != null) queryBuilder.append(" WHERE ${filter.toSqlWhereString()}")
        if (!orderBy.isNullOrEmpty()) queryBuilder.append(" ORDER BY ${orderBy.toSqlOrderByString()}")
        if (limit != null) queryBuilder.append(" LIMIT $limit")
        if (offset != null) queryBuilder.append(" OFFSET $offset")
    }
    return rawQuery(
        query = queryBuilder.toString(),
        args = filter?.toSqlWhereArgsList(),
    )
}

/**
 * Runs [action] in a transaction of this connection, committed once [action] returns and rolled
 * back when it - or the commit - throws.
 *
 * Issued as statements rather than asked of the driver, so that they reach a logging connection
 * like any other, and any SQL database understands them.
 */
internal suspend fun <T> SQLConnection.transaction(action: suspend () -> T): T {
    rawQuery("BEGIN")
    try {
        return action().also { rawQuery("COMMIT") }
    } catch (e: Throwable) {
        // Rolled back even when the coroutine is cancelled: a transaction left open would take in
        // every statement that follows it on the connection.
        withContext(NonCancellable) {
            try {
                rawQuery("ROLLBACK")
            } catch (rollbackError: Throwable) {
                e.addSuppressed(rollbackError)
            }
        }
        throw e
    }
}

internal suspend fun SQLConnection.upsert(
    tableName: String,
    values: PrimitiveRekordValues,
    filter: Filter?,
) {
    if (values.isEmpty()) return
    val updateBuilder = StringBuilder()
    updateBuilder.append("UPDATE OR IGNORE $tableName SET ")
    updateBuilder.append(values.keys.joinToString(listSeparator) { "$it = ?" })
    if (filter != null) {
        updateBuilder.append(" WHERE ${filter.toSqlWhereString()}")
    }
    rawQuery(
        query = updateBuilder.toString(),
        args = values.values + filter?.toSqlWhereArgsList().orEmpty(),
    )
    insertOrIgnore(tableName, values)
}

/** Inserts [values] as a row unless one with the same key is there already, which is kept. */
internal suspend fun SQLConnection.insertOrIgnore(
    tableName: String,
    values: PrimitiveRekordValues,
) {
    if (values.isEmpty()) return
    val insertBuilder = StringBuilder()
    insertBuilder.append("INSERT OR IGNORE INTO $tableName (")
    insertBuilder.append(values.keys.joinToString(listSeparator))
    insertBuilder.append(") VALUES (")
    insertBuilder.append(values.keys.joinToString(listSeparator) { "?" })
    insertBuilder.append(")")
    rawQuery(query = insertBuilder.toString(), args = values.values)
}

internal suspend fun SQLConnection.delete(tableName: String, filter: Filter?) {
    val queryBuilder = StringBuilder()
    queryBuilder.append("DELETE FROM $tableName")
    if (filter != null) {
        queryBuilder.append(" WHERE ${filter.toSqlWhereString()}")
    }
    rawQuery(
        query = queryBuilder.toString(),
        args = filter?.toSqlWhereArgsList(),
    )
}

internal suspend fun SQLConnection.count(tableName: String, filter: Filter?): Int =
    select(
        tableName = tableName,
        columnNames = listOf("COUNT(*)"),
        filter = filter,
        limit = 1,
    ).firstIntValue() ?: 0

internal fun Filter.qualify(tableName: String): Filter = when (this) {
    is Filter.And -> Filter.And(filters.map { it.qualify(tableName) })
    is Filter.Or -> Filter.Or(filters.map { it.qualify(tableName) })
    is Filter.Not -> Filter.Not(filter.qualify(tableName) as FieldFilter)
    is FieldFilter.Nested -> this
    is FieldFilter.InList -> if ("." in field) this else Filter.InList("$tableName.$field", list)
    is FieldFilter.Contains -> if ("." in field) this else Filter.Contains("$tableName.$field", value)
    is FieldValueFilter.Equals -> if ("." in field) this else Filter.Equals("$tableName.$field", value)
    is FieldValueFilter.LessThan -> if ("." in field) this else Filter.LessThan("$tableName.$field", value)
    is FieldValueFilter.LessThanOrEquals -> if ("." in field) this else Filter.LessThanOrEquals("$tableName.$field", value)
    is FieldValueFilter.GreaterThan -> if ("." in field) this else Filter.GreaterThan("$tableName.$field", value)
    is FieldValueFilter.GreaterThanOrEquals -> if ("." in field) this else Filter.GreaterThanOrEquals("$tableName.$field", value)
}

private fun Filter.toSqlWhereString(): String = when (this) {
    is Filter.And -> filters.toSqlWhereString(separator = " AND ")
    is Filter.Or -> filters.toSqlWhereString(separator = " OR ")
    is Filter.Not -> "${filter.field} ${toSqlOperationString()} ?"
    is FieldFilter.Nested -> filter.toSqlWhereString().run { substring(1, length - 1) }
    is FieldFilter.InList -> "$field ${toSqlOperationString()} (${
        list.joinToString(listSeparator) { "?" }
    })"
    is FieldFilter -> "$field ${toSqlOperationString()} ?"
}.let { "($it)" }

private fun Filter.toSqlWhereArgsList(): List<PrimitiveRekordValue?> = when (this) {
    is Filter.And -> filters.toSqlWhereArgsList()
    is Filter.Or -> filters.toSqlWhereArgsList()
    is Filter.Not -> filter.toSqlWhereArgsList()
    is FieldFilter.Nested -> filter.toSqlWhereArgsList()
    is FieldFilter.InList -> list
    is FieldFilter.Contains -> listOf("%$value%")
    is FieldValueFilter -> listOf(value)
}

private fun Filter.toSqlOperationString(): String = when (this) {
    is FieldValueFilter.Equals -> if (value != null) "=" else "IS"
    is FieldValueFilter.LessThan -> "<"
    is FieldValueFilter.LessThanOrEquals -> "<="
    is FieldValueFilter.GreaterThan -> ">"
    is FieldValueFilter.GreaterThanOrEquals -> ">="
    is FieldFilter.InList -> "IN"
    is FieldFilter.Contains -> "LIKE"
    is Filter.Not -> filter.toSqlOperationString()
    else -> ""
}

private fun Filter.Not.toSqlOperationString(): String {
    val operation: String = filter.toSqlOperationString()
    if (filter is FieldValueFilter.Equals) {
        if ((filter as FieldValueFilter).value == null) {
            return "$operation NOT"
        }
        return "!$operation"
    }
    return "NOT $operation"
}

private fun Iterable<Filter>.toSqlWhereString(separator: String): String {
    return map { filter -> filter.toSqlWhereString() }
        .filter { condition -> condition.isNotEmpty() }
        .joinToString(separator)
}

private fun Iterable<Filter>.toSqlWhereArgsList(): List<PrimitiveRekordValue?> {
    return flatMap { filter -> filter.toSqlWhereArgsList() }
}

private fun List<Order>.toSqlOrderByString(): String =
    joinToString(listSeparator) { order -> order.toSqlString() }

private fun Order.toSqlString(): String = when (this) {
    is Order.Ascending -> "$field ASC"
    is Order.Descending -> "$field DESC"
}