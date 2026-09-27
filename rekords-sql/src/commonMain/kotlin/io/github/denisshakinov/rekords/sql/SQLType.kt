package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import kotlin.reflect.KType

enum class SQLType {
    Integer, IntegerNullable, Real, RealNullable, Text, TextNullable,
}

internal fun KType.toSQLType(): SQLType {
    return when (this.classifier) {
        Long::class -> if (isMarkedNullable) SQLType.IntegerNullable else SQLType.Integer
        Int::class -> if (isMarkedNullable) SQLType.IntegerNullable else SQLType.Integer
        Float::class -> if (isMarkedNullable) SQLType.RealNullable else SQLType.Real
        Double::class -> if (isMarkedNullable) SQLType.RealNullable else SQLType.Real
        Boolean::class -> if (isMarkedNullable) SQLType.IntegerNullable else SQLType.Integer
        String::class -> if (isMarkedNullable) SQLType.TextNullable else SQLType.Text
        else -> throw IllegalArgumentException("Unsupported type: $this")
    }
}

internal val SQLType.isNullable: Boolean
    get() = this == SQLType.IntegerNullable || this == SQLType.RealNullable || this == SQLType.TextNullable

internal fun SQLType.asNullable(): SQLType = when (this) {
    SQLType.Integer, SQLType.IntegerNullable -> SQLType.IntegerNullable
    SQLType.Real, SQLType.RealNullable -> SQLType.RealNullable
    SQLType.Text, SQLType.TextNullable -> SQLType.TextNullable
}

/**
 * The value of this type that stands for none, which a column a migration adds is filled with
 * when it is given none, or null for a nullable column, which is then left without a value.
 */
internal fun SQLType.noneValue(): PrimitiveRekordValue? = when (this) {
    SQLType.Integer -> 0L
    SQLType.Real -> 0.0
    SQLType.Text -> ""
    SQLType.IntegerNullable, SQLType.RealNullable, SQLType.TextNullable -> null
}

/**
 * Whether [value], encoded the way a rekord's fields are, is one a column of this type holds.
 * SQLite would take any value into any column, and one of the wrong kind would only be found out
 * when the rekord holding it is read.
 */
internal fun SQLType.holds(value: PrimitiveRekordValue): Boolean = when (this) {
    SQLType.Integer, SQLType.IntegerNullable -> value is Int || value is Long || value is Boolean
    SQLType.Real, SQLType.RealNullable -> value is Float || value is Double
    SQLType.Text, SQLType.TextNullable -> value is String
}

internal fun SQLType.toSqlStringType(): String {
    return when (this) {
        SQLType.Integer -> "INTEGER NOT NULL"
        SQLType.IntegerNullable -> "INTEGER"
        SQLType.Real -> "REAL NOT NULL"
        SQLType.RealNullable -> "REAL"
        SQLType.Text -> "TEXT NOT NULL"
        SQLType.TextNullable -> "TEXT"
    }
}

fun String.toSQLType(): SQLType = when (this) {
    "INTEGER NOT NULL" -> SQLType.Integer
    "INTEGER" -> SQLType.IntegerNullable
    "REAL NOT NULL" -> SQLType.Real
    "REAL" -> SQLType.RealNullable
    "TEXT NOT NULL" -> SQLType.Text
    "TEXT" -> SQLType.TextNullable
    else -> throw IllegalArgumentException("Unsupported SQL type string: $this")
}