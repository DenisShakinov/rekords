package io.github.denisshakinov.rekords.sqlite

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import io.github.denisshakinov.rekords.core.MutablePrimitiveRekordValues
import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.PrimitiveRekordValues
import io.github.denisshakinov.rekords.sql.SQLColumn
import io.github.denisshakinov.rekords.sql.SQLConnection
import io.github.denisshakinov.rekords.sql.SQLDriver
import io.github.denisshakinov.rekords.sql.toSQLType

/**
 * Adapts an `androidx.sqlite` [SQLiteDriver] to the driver the SQL rekords editor speaks.
 *
 * Every target of this module gets the same adapter because it only touches the driver API. The
 * web targets are left out of the module for that reason: there `SQLiteDriver.open` and
 * `SQLiteConnection.prepare` are suspending members of a differently shaped `actual` interface,
 * which needs an adapter of its own.
 */
internal fun SQLiteDriver.asSQLDriver(): SQLDriver = SQLiteSQLDriver(this)

private class SQLiteSQLDriver(private val driver: SQLiteDriver) : SQLDriver {

    override suspend fun open(databasePath: String): SQLConnection =
        SQLiteSQLConnection(driver.open(databasePath))
}

/**
 * Keeps the statements it has prepared, [MAX_CACHED_STATEMENTS] of those used last, so that a
 * statement run again - one per rekord written, a write of many - is not compiled anew each time.
 */
private class SQLiteSQLConnection(
    private val connection: SQLiteConnection,
) : SQLConnection {

    /** Keyed by their SQL, held in the order they were last used in, the least recent first. */
    private val statements: MutableMap<String, SQLiteStatement> = mutableMapOf()

    override suspend fun rawQuery(
        query: String,
        args: Iterable<PrimitiveRekordValue?>?,
    ): Iterable<PrimitiveRekordValues> {
        // Taken out while it runs, so that the cache only ever holds statements ready to be run.
        val statement: SQLiteStatement = statements.remove(query) ?: connection.prepare(sql = query)
        val result: MutableList<PrimitiveRekordValues> = mutableListOf()
        try {
            args?.forEachIndexed { index, arg ->
                statement.bind(index + 1, arg)
            }
            // consider creating custom iterable to not iterate over the whole result twice
            while (statement.step()) {
                val values: MutablePrimitiveRekordValues = mutableMapOf()
                (0..<statement.getColumnCount()).forEach { columnIndex: Int ->
                    values[statement.getColumnName(columnIndex)] =
                        statement.getValue(columnIndex)
                }
                result.add(values)
            }
            statement.reset()
            statement.clearBindings()
        } catch (e: Throwable) {
            // Whatever state a failure left the statement in, it is not kept to be run again.
            statement.close()
            throw e
        }
        cache(query, statement)
        return result
    }

    private fun cache(query: String, statement: SQLiteStatement) {
        statements[query] = statement
        if (statements.size > MAX_CACHED_STATEMENTS) {
            val leastRecent = statements.keys.first()
            statements.remove(leastRecent)?.close()
        }
    }

    override suspend fun tableSchema(tableName: String): List<SQLColumn> {
        return rawQuery("PRAGMA table_info($tableName)").map { row ->
            // The type is reported as declared without its constraints, NOT NULL being a column
            // of its own - read alone, every column would come back nullable.
            val declaredType = row["type"] as String
            val notNull = (row["notnull"] as Long) != 0L
            SQLColumn(
                name = row["name"] as String,
                isPrimaryKey = (row["pk"] as Long) != 0L,
                type = (if (notNull) "$declaredType NOT NULL" else declaredType).toSQLType(),
            )
        }
    }

    override fun close() {
        statements.values.forEach { it.close() }
        statements.clear()
        connection.close()
    }

    private companion object {
        /**
         * Enough for the statements an editor keeps running - a write and the reads it is
         * interleaved with, over every table of a schema - while the SQL a filter makes differs
         * with the values it is given and is not let grow the cache without end.
         */
        const val MAX_CACHED_STATEMENTS = 64
    }
}

private fun SQLiteStatement.bind(index: Int, value: PrimitiveRekordValue?) {
    when (value) {
        is Double -> bindDouble(index, value)
        is Float -> bindFloat(index, value)
        is Long -> bindLong(index, value)
        is Int -> bindInt(index, value)
        is Boolean -> bindBoolean(index, value)
        is String -> bindText(index, value)
        null -> bindNull(index)
        else -> throw IllegalArgumentException("Cannot bind $value in SQLiteStatement")
    }
}

private fun SQLiteStatement.getValue(columnIndex: Int): PrimitiveRekordValue? {
    return when (getColumnType(columnIndex)) {
        SQLITE_DATA_INTEGER -> getLong(columnIndex)
        SQLITE_DATA_FLOAT -> getDouble(columnIndex)
        SQLITE_DATA_TEXT -> getText(columnIndex)
        SQLITE_DATA_NULL -> null
        SQLITE_DATA_BLOB -> throw IllegalArgumentException("ByteArray is not supported PrimitiveRekordValue")
        else -> null
    }
}
