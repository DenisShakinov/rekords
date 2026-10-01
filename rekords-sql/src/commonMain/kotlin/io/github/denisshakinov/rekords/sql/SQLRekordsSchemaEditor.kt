@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.FieldWithType
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.RekordKType
import io.github.denisshakinov.rekords.core.RekordListKType
import io.github.denisshakinov.rekords.core.RekordsSchemaEditor
import io.github.denisshakinov.rekords.core.allFields
import io.github.denisshakinov.rekords.core.elementAllFields
import io.github.denisshakinov.rekords.core.elementRekordType
import io.github.denisshakinov.rekords.core.field
import io.github.denisshakinov.rekords.core.rekordSchema
import io.github.denisshakinov.rekords.core.rekordType
import io.github.denisshakinov.rekords.core.toStoredValue
import io.github.denisshakinov.rekords.core.type
import kotlin.reflect.KClass

class SQLRekordsSchemaEditor(
    private val connection: suspend () -> SQLConnection,
) : RekordsSchemaEditor() {

    override suspend fun version(): Int {
        ensureMetaTable()
        return connection().select(
            tableName = META_TABLE,
            columnNames = listOf(VALUE_COLUMN),
            condition = Filter.Equals(KEY_COLUMN, VERSION_KEY).toSQLCondition(META_TABLE),
            limit = 1,
        ).firstStringValue()?.toInt() ?: 0
    }

    override suspend fun updateVersion(newVersion: Int) {
        ensureMetaTable()
        connection().upsert(
            tableName = META_TABLE,
            values = mapOf(
                KEY_COLUMN to VERSION_KEY,
                VALUE_COLUMN to newVersion.toString(),
            ),
            condition = Filter.Equals(KEY_COLUMN, VERSION_KEY).toSQLCondition(META_TABLE),
        )
    }

    override suspend fun addRekordType(rekordClass: KClass<*>) {
        val tableName = rekordClass.rekordType()
        val fields = rekordClass.allFields()
        val columns = mutableListOf<SQLColumn>()
        val indices = mutableListOf<String>()
        // Read before any table is built: a junction table is keyed by the ids of its parent, and
        // a list field declared ahead of them would otherwise be given only the ids that had been
        // walked past by then.
        val parentIdFields: List<Pair<Field, SQLType>> = fields.primitiveIdFields()

        for ((fieldAnnotation, fieldType) in fields) {
            when (fieldType) {
                is RekordListKType ->
                    createJunctionTable(tableName, fieldAnnotation.name, parentIdFields, fieldType)
                is RekordKType ->
                    fieldType.allFields().primitiveIdFields().forEach { (childField, sqlType) ->
                        val nullable = if (fieldType.isMarkedNullable) sqlType.asNullable() else sqlType
                        columns.add(
                            SQLColumn("${fieldAnnotation.name}_${childField.name}", isPrimaryKey = false, nullable)
                        )
                    }
                else -> {
                    columns.add((fieldAnnotation to fieldType).toSqlColumn())
                    if (fieldAnnotation.searchable) indices.add(fieldAnnotation.name)
                }
            }
        }
        connection().createTable(tableName, columns, indices)
    }

    private suspend fun createJunctionTable(
        parentRekordType: String,
        fieldName: String,
        parentIdFields: List<Pair<Field, SQLType>>,
        listKType: RekordListKType,
    ) {
        val childRekordType = listKType.elementRekordType()
        val columns = buildList {
            parentIdFields.forEach { (field, sqlType) ->
                add(SQLColumn("${parentRekordType}_${field.name}", isPrimaryKey = true, sqlType))
            }
            listKType.elementAllFields().primitiveIdFields().forEach { (childField, sqlType) ->
                add(SQLColumn("${childRekordType}_${childField.name}", isPrimaryKey = true, sqlType))
            }
        }
        connection().createTable("${parentRekordType}_${fieldName}", columns)
    }

    override suspend fun removeRekordType(rekordType: String) {
        listFields(rekordType)
            .forEach { connection().rawQuery("DROP TABLE IF EXISTS ${rekordType}_${it.field.name}") }
        connection().rawQuery("DROP TABLE IF EXISTS $rekordType")
    }

    /**
     * Renames the table of [oldRekordType], and the columns its ids are kept in wherever they are
     * named after it: in the junction tables of the lists holding its rekords, and in those of its
     * own lists.
     *
     * The schema a migration runs for knows the type by [newRekordType], unless the type is renamed
     * on its way to that name - as one renamed twice is - so its fields are read under either.
     */
    override suspend fun renameRekordType(oldRekordType: String, newRekordType: String) {
        val typeSchema = rekordSchema(newRekordType).ifEmpty { rekordSchema(oldRekordType) }.values
        val idColumnRenames = typeSchema
            .filter { it.field.id && it.type !is RekordKType && it.type !is RekordListKType }
            .associate { (field, _) -> "${oldRekordType}_${field.name}" to "${newRekordType}_${field.name}" }
        if (idColumnRenames.isNotEmpty()) {
            schema.rekordTypes.forEach { parentClass ->
                val parentType = parentClass.rekordType()
                parentClass.allFields()
                    .filter { (_, ktype) ->
                        ktype is RekordListKType && ktype.elementRekordType() in setOf(oldRekordType, newRekordType)
                    }
                    .forEach { (field, _) ->
                        recreateTable("${parentType}_${field.name}", columnRenames = idColumnRenames)
                    }
            }
        }
        connection().rawQuery("ALTER TABLE $oldRekordType RENAME TO $newRekordType")
        typeSchema
            .filter { it.type is RekordListKType }
            .forEach { (field, _) ->
                val junctionTable = "${newRekordType}_${field.name}"
                connection().rawQuery("ALTER TABLE ${oldRekordType}_${field.name} RENAME TO $junctionTable")
                if (idColumnRenames.isNotEmpty()) recreateTable(junctionTable, columnRenames = idColumnRenames)
            }
    }

    override suspend fun addField(
        rekordType: String,
        field: FieldWithType,
        defaultValue: PrimitiveRekordValue?,
    ) {
        val column = field.toSqlColumn()
        val storedValue = defaultValue.toStoredValue()
        require(storedValue == null || column.type.holds(storedValue)) {
            "$defaultValue cannot be the default value of ${field.field.name} in $rekordType"
        }
        // The column is left without a DEFAULT either way, as one the table was created with is:
        // with one, a rekord written without the field would be filled in rather than not stored.
        val fillValue = storedValue ?: column.type.noneValue()
        if (!column.type.isNullable) {
            // SQLite only adds a NOT NULL column with a DEFAULT, so the table is rebuilt instead.
            recreateTable(rekordType, columnsToAdd = listOf(column to checkNotNull(fillValue)))
            return
        }
        connection().rawQuery(
            "ALTER TABLE $rekordType ADD COLUMN ${column.name} ${column.type.toSqlStringType()}"
        )
        if (fillValue != null) {
            connection().rawQuery("UPDATE $rekordType SET ${column.name} = ?", listOf(fillValue))
        }
        if (field.field.searchable) connection().createIndex(rekordType, column.name)
    }

    override suspend fun removeField(rekordType: String, fieldName: String) {
        if (isCompositeListField(rekordType, fieldName)) {
            connection().rawQuery("DROP TABLE IF EXISTS ${rekordType}_${fieldName}")
        } else {
            recreateTable(rekordType, columnsToRemove = setOf(fieldName))
        }
    }

    override suspend fun renameField(
        rekordType: String,
        oldFieldName: String,
        newFieldName: String,
    ) {
        if (isCompositeListField(rekordType, oldFieldName)) {
            connection().rawQuery(
                "ALTER TABLE ${rekordType}_${oldFieldName} RENAME TO ${rekordType}_${newFieldName}"
            )
        } else {
            recreateTable(rekordType, columnRenames = mapOf(oldFieldName to newFieldName))
        }
    }

    private fun isCompositeListField(rekordType: String, fieldName: String): Boolean =
        rekordSchema(rekordType)[fieldName]?.type is RekordListKType

    private fun listFields(rekordType: String) =
        rekordSchema(rekordType).values.filter { it.type is RekordListKType }

    /**
     * Builds [tableName] again with its columns changed, and copies its rows over.
     *
     * @param columnsToAdd the columns to add, each with the value the rows copied are given.
     */
    private suspend fun recreateTable(
        tableName: String,
        columnsToRemove: Set<String> = emptySet(),
        columnRenames: Map<String, String> = emptyMap(),
        columnsToAdd: List<Pair<SQLColumn, PrimitiveRekordValue>> = emptyList(),
    ) {
        val originalColumns = connection().tableSchema(tableName).filter { it.name !in columnsToRemove }
        val renamedColumns = originalColumns.map { col ->
            columnRenames[col.name]?.let { newName -> SQLColumn(newName, col.isPrimaryKey, col.type) } ?: col
        } + columnsToAdd.map { (column, _) -> column }
        val tmpTable = "${tableName}_new"
        connection().rawQuery(buildCreateTableDDL(tmpTable, renamedColumns, ifNotExists = false))
        val oldCols = (originalColumns.map { it.name } + columnsToAdd.map { "?" }).joinToString(", ")
        val newCols = renamedColumns.joinToString(", ") { it.name }
        connection().rawQuery(
            "INSERT INTO $tmpTable ($newCols) SELECT $oldCols FROM $tableName",
            columnsToAdd.map { (_, value) -> value },
        )
        connection().rawQuery("DROP TABLE $tableName")
        connection().rawQuery("ALTER TABLE $tmpTable RENAME TO $tableName")
        // The table's indexes went with it. They are built again from the schema, the way
        // addRekordType builds them, for the searchable fields the new table has a column for: an
        // index follows a field renamed, goes with one removed and comes with one added. A
        // junction table, not being a rekord type, has none.
        val columnNames = renamedColumns.map { it.name }.toSet()
        rekordSchema(tableName).values
            .filter { it.field.searchable && it.field.name in columnNames }
            .forEach { connection().createIndex(tableName, it.field.name) }
    }

    private suspend fun ensureMetaTable() {
        connection().rawQuery(
            "CREATE TABLE IF NOT EXISTS $META_TABLE " +
                    "($KEY_COLUMN TEXT NOT NULL PRIMARY KEY, $VALUE_COLUMN TEXT NOT NULL)"
        )
    }

    companion object {
        private const val META_TABLE = "_meta"
        private const val KEY_COLUMN = "key"
        private const val VALUE_COLUMN = "value"
        private const val VERSION_KEY = "version"
    }
}

private fun List<FieldWithType>.primitiveIdFields(): List<Pair<Field, SQLType>> = buildList {
    for ((field, ktype) in this@primitiveIdFields) {
        if (!field.id) continue
        if (ktype is RekordKType || ktype is RekordListKType) continue
        add(field to ktype.toSQLType())
    }
}

private suspend fun SQLConnection.createTable(
    tableName: String,
    columns: List<SQLColumn>,
    indices: List<String> = emptyList(),
) {
    rawQuery(buildCreateTableDDL(tableName, columns, ifNotExists = true))
    indices.forEach { createIndex(tableName, it) }
}

private fun buildCreateTableDDL(
    tableName: String,
    columns: List<SQLColumn>,
    ifNotExists: Boolean,
): String = buildString {
    append("CREATE TABLE ")
    if (ifNotExists) append("IF NOT EXISTS ")
    append("$tableName (")
    columns.forEachIndexed { index, column ->
        if (index > 0) append(", ")
        append("${column.name} ${column.type.toSqlStringType()}")
    }
    val primaryKeys = columns.filter { it.isPrimaryKey }.map { it.name }
    if (primaryKeys.isNotEmpty()) {
        append(", PRIMARY KEY (${primaryKeys.joinToString(", ")})")
    }
    append(")")
}

private suspend fun SQLConnection.createIndex(tableName: String, columnName: String) {
    rawQuery("CREATE INDEX IF NOT EXISTS idx_${tableName}_${columnName} ON $tableName ($columnName)")
}