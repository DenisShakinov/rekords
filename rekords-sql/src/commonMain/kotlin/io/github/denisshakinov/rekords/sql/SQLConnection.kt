package io.github.denisshakinov.rekords.sql

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

internal suspend fun SQLConnection.select(
    tableName: String,
    columnNames: List<String>? = null,
    condition: SQLCondition? = null,
    orderBy: List<Order>? = null,
    limit: Int? = null,
    offset: Int? = null,
): Iterable<PrimitiveRekordValues> {
    val queryBuilder = StringBuilder()
    queryBuilder.append("SELECT ")
    queryBuilder.append(columnNames?.joinToString(listSeparator) ?: "*")
    queryBuilder.append(" FROM $tableName")
    if (condition != null) queryBuilder.append(" WHERE ${condition.sql}")
    if (!orderBy.isNullOrEmpty()) queryBuilder.append(" ORDER BY ${orderBy.toSqlOrderByString()}")
    if (limit != null) queryBuilder.append(" LIMIT $limit")
    // SQLite takes an OFFSET only after a LIMIT, which -1 leaves unbounded.
    if (offset != null) queryBuilder.append("${if (limit == null) " LIMIT -1" else ""} OFFSET $offset")
    return rawQuery(
        query = queryBuilder.toString(),
        args = condition?.args,
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
    condition: SQLCondition?,
) {
    if (values.isEmpty()) return
    val updateBuilder = StringBuilder()
    updateBuilder.append("UPDATE OR IGNORE $tableName SET ")
    updateBuilder.append(values.keys.joinToString(listSeparator) { "$it = ?" })
    if (condition != null) {
        updateBuilder.append(" WHERE ${condition.sql}")
    }
    rawQuery(
        query = updateBuilder.toString(),
        args = values.values + condition?.args.orEmpty(),
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

internal suspend fun SQLConnection.delete(tableName: String, condition: SQLCondition?) {
    val queryBuilder = StringBuilder()
    queryBuilder.append("DELETE FROM $tableName")
    if (condition != null) {
        queryBuilder.append(" WHERE ${condition.sql}")
    }
    rawQuery(
        query = queryBuilder.toString(),
        args = condition?.args,
    )
}

internal suspend fun SQLConnection.count(tableName: String, condition: SQLCondition?): Int =
    select(
        tableName = tableName,
        columnNames = listOf("COUNT(*)"),
        condition = condition,
    ).firstIntValue() ?: 0

private fun List<Order>.toSqlOrderByString(): String =
    joinToString(listSeparator) { order -> order.toSqlString() }

private fun Order.toSqlString(): String = when (this) {
    is Order.Ascending -> "$field ASC"
    is Order.Descending -> "$field DESC"
}