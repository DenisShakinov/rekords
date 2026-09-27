package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.FieldWithType
import io.github.denisshakinov.rekords.core.field
import io.github.denisshakinov.rekords.core.type

/**
 * Describes a single column in a SQL table, used for schema introspection and table recreation.
 *
 * @property name the column name.
 * @property isPrimaryKey whether this column is part of the primary key.
 * @property type the SQL type declaration (e.g. "INTEGER NOT NULL", "TEXT").
 */
class SQLColumn(
    val name: String,
    val isPrimaryKey: Boolean,
    val type: SQLType,
)

fun FieldWithType.toSqlColumn(): SQLColumn =
    SQLColumn(name = field.name, isPrimaryKey = field.id, type = type.toSQLType())