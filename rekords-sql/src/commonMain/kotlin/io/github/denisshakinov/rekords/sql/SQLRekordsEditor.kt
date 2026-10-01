@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.PrimitiveRekordValues
import io.github.denisshakinov.rekords.core.RekordKType
import io.github.denisshakinov.rekords.core.RekordListKType
import io.github.denisshakinov.rekords.core.RekordValue
import io.github.denisshakinov.rekords.core.RekordValues
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsSchemaEditor
import io.github.denisshakinov.rekords.core.elementRekordType
import io.github.denisshakinov.rekords.core.rekordSchema
import io.github.denisshakinov.rekords.core.rekordType
import io.github.denisshakinov.rekords.core.runOnEditor
import io.github.denisshakinov.rekords.core.type
import kotlin.reflect.KType

/**
 * A [RekordsEditor] storing rekords in the SQL database at [databasePath], reached through
 * [sqlDriver].
 *
 * One connection is opened on first use and kept for as long as the editor is, which is what a
 * SQL database expects - opening one is the expensive part. [close] gives it up, and the editor
 * carries on: the next operation opens another.
 *
 * Operations are not serialized here. A connection is not made to be used from two places at
 * once, so the editor has to be given to a single [io.github.denisshakinov.rekords.core.RekordsStore],
 * which runs one operation at a time, and reached through it alone.
 */
class SQLRekordsEditor(
    private val databasePath: String,
    private val sqlDriver: SQLDriver,
) : RekordsEditor {

    // Reached through SuspendLazy rather than held outright, so that the database is opened by
    // the first operation that needs it and opened again after a close.
    private val connection = SuspendLazy { sqlDriver.open(databasePath) }

    override val schemaEditor: RekordsSchemaEditor = SQLRekordsSchemaEditor { connection() }

    override fun close() {
        connection.reset()?.close()
    }

    /** The database's own transaction, which every operation of [action] runs in. */
    override suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): T =
        connection().transaction { runOnEditor(this, action) }

    override suspend fun put(rekordType: String, values: RekordValues, filter: Filter?) =
        upsertWithComposites(rekordType, values, filter)

    private suspend fun upsertWithComposites(rekordType: String, values: RekordValues, filter: Filter?) {
        val schema = rekordSchema(rekordType)
        val primitiveValues = buildMap {
            for ((fieldName, value) in values) {
                val type = schema[fieldName]?.type
                when {
                    value is RekordValue.Composite ->
                        idFieldNames(value.rekordType).forEach { childId ->
                            put("${fieldName}_$childId", (value.values[childId] as? RekordValue.Primitive)?.value)
                        }
                    // A rekord written as none leaves the rekord holding it referring to none.
                    value == null && type is RekordKType ->
                        idFieldNames(type.rekordType()).forEach { childId -> put("${fieldName}_$childId", null) }
                    value is RekordValue.CompositeList || type is RekordListKType -> {}
                    else -> put(fieldName, (value as? RekordValue.Primitive)?.value)
                }
            }
        }
        connection().upsert(rekordType, primitiveValues, filter?.toCondition(rekordType))
        for ((fieldName, value) in values) {
            when {
                value is RekordValue.Composite -> {
                    val f = buildIdFilter(value.rekordType, value.values)
                    upsertWithComposites(value.rekordType, value.values, f)
                }
                value is RekordValue.CompositeList -> {
                    val junctionTable = junctionTable(rekordType, fieldName)
                    val parentIdValues = prefixedIdValues(rekordType, values)
                    // The list given replaces the list stored, so an item dropped from it loses
                    // its link too - a partial list written over a fuller one would otherwise
                    // leave what it no longer holds behind, and the parent would keep reporting
                    // it. Only the links go: the items themselves are shared with other parents.
                    unlinkListItems(junctionTable, parentIdValues)
                    value.list.forEach { item ->
                        val f = buildIdFilter(item.rekordType, item.values)
                        upsertWithComposites(item.rekordType, item.values, f)
                        // A link is nothing but its key, so there is nothing to update in one
                        // already there - only one that is not to insert. The links are inserted
                        // in the order of the list, which reading them back follows.
                        connection().insertOrIgnore(
                            junctionTable,
                            parentIdValues + prefixedIdValues(item.rekordType, item.values),
                        )
                    }
                }
                // A list written as none holds nothing.
                value == null && schema[fieldName]?.type is RekordListKType ->
                    unlinkListItems(junctionTable(rekordType, fieldName), prefixedIdValues(rekordType, values))
            }
        }
    }

    /**
     * Drops every link [parentIdValues] has in [junctionTable]. A parent that did not bring all
     * of its ids cannot be told apart from the others, and nothing is deleted rather than too
     * much: the links it does have are then only added to.
     */
    private suspend fun unlinkListItems(junctionTable: String, parentIdValues: PrimitiveRekordValues) {
        if (parentIdValues.isEmpty() || parentIdValues.values.any { it == null }) return
        connection().delete(junctionTable, buildFilter(parentIdValues)?.toSQLCondition(junctionTable))
    }

    // Extracts id field values prefixed with rekordType to avoid column name collisions in junction tables.
    // e.g. Game(id=1) -> {"Game_id": 1}
    private fun prefixedIdValues(rekordType: String, values: RekordValues): PrimitiveRekordValues =
        idFieldNames(rekordType)
            .associate { "${rekordType}_$it" to (values[it] as? RekordValue.Primitive)?.value }

    private fun buildIdFilter(rekordType: String, values: RekordValues): Filter? =
        buildFilter(idFieldNames(rekordType).associateWith { (values[it] as? RekordValue.Primitive)?.value })

    private fun buildFilter(values: PrimitiveRekordValues): Filter? {
        val filters = values.mapNotNull { (name, v) -> v?.let { Filter.Equals(name, it) } }
        return when (filters.size) {
            0 -> null
            1 -> filters.first()
            else -> Filter.And(filters)
        }
    }

    /**
     * Deletes the rekords [filter] selects, and then their links to the rekords of their lists, by
     * the ids of the rekords deleted: [filter] may look into the very lists whose links go, and is
     * run while they are still there.
     */
    override suspend fun delete(rekordType: String, filter: Filter?) {
        val condition = filter?.toCondition(rekordType)
        val listFields = rekordSchema(rekordType).values.filter { it.type is RekordListKType }
        val parentIds = idFieldNames(rekordType)
        val parentKeys = if (condition != null && listFields.isNotEmpty() && parentIds.isNotEmpty()) {
            connection().select(rekordType, parentIds, condition).map { row -> parentIds.map { row[it] } }
        } else {
            null
        }
        connection().delete(rekordType, condition)
        for ((fieldAnnotation, _) in listFields) {
            val junctionTable = junctionTable(rekordType, fieldAnnotation.name)
            when {
                condition == null -> connection().delete(junctionTable, condition = null)
                parentKeys != null -> forEachKeyChunk(parentIds.map { "${rekordType}_$it" }, parentKeys) { keys ->
                    connection().delete(junctionTable, keys.toSQLCondition(junctionTable))
                }
            }
        }
    }

    /**
     * Reads the rekords [filter] selects from the table of [rekordType] alone, so that [limit] and
     * [offset] count rekords, and then what they nest, field by field and level by level: each
     * nested rekord type is read once per field it is in, for all the rekords read at the level
     * above. A rekord nested at any depth is read that way, as is one type nested in several
     * fields, and as many lists as a rekord has without their items being multiplied by each other.
     */
    override suspend fun query(
        rekordType: String,
        fields: List<String>?,
        filter: Filter?,
        orderBy: List<Order>?,
        limit: Int?,
        offset: Int?,
    ): List<RekordValues> {
        val condition = filter?.toCondition(rekordType)
        if (rekordSchema(rekordType).isEmpty()) {
            // A table of no rekord type the schema declares is read as the rows it holds.
            return connection()
                .select(rekordType, fields, condition, orderBy, limit, offset)
                .map { row -> row.mapValues { (_, value) -> value?.let { RekordValue.Primitive(it) } } }
        }
        val rows = connection().select(rekordType, columnNames = null, condition, orderBy, limit, offset).toList()
        return read(rekordType, rows, fields)
    }

    /** The rekords of [rekordType] [rows] hold, with the rekords they nest read as well. */
    private suspend fun read(
        rekordType: String,
        rows: List<PrimitiveRekordValues>,
        fields: List<String>?,
    ): List<RekordValues> {
        val schema = rekordSchema(rekordType)
        val rekords: List<MutableMap<String, RekordValue?>> = rows.map { row ->
            buildMap {
                for ((field, type) in schema.values) {
                    if (type is RekordKType || type is RekordListKType) continue
                    if (fields != null && field.name !in fields) continue
                    put(field.name, row[field.name]?.let { RekordValue.Primitive(coercePrimitive(it, type)) })
                }
            }.toMutableMap()
        }
        for ((field, type) in schema.values) {
            when (type) {
                is RekordKType -> {
                    val childType = type.rekordType()
                    val childIds = idFieldNames(childType)
                    // The ids a row refers to its rekord by, none of which is null if it refers to one.
                    val keys = rows.map { row ->
                        childIds.map { row["${field.name}_$it"] }.takeIf { key -> key.isNotEmpty() && key.none { it == null } }
                    }
                    val children = readByIds(childType, keys.filterNotNull().distinct())
                    rekords.forEachIndexed { index, rekord -> rekord[field.name] = keys[index]?.let { children[it] } }
                }
                is RekordListKType -> {
                    val childType = type.elementRekordType()
                    val parentIds = idFieldNames(rekordType)
                    val childIds = idFieldNames(childType)
                    val junctionTable = junctionTable(rekordType, field.name)
                    val parentColumns = parentIds.map { "${rekordType}_$it" }
                    val childColumns = childIds.map { "${childType}_$it" }
                    val parentKeys = rows.map { row -> parentIds.map { row[it] } }
                    val links = selectByKeys(junctionTable, parentColumns, parentKeys.distinct(), orderBy = INSERTION_ORDER)
                    val linked: Map<List<PrimitiveRekordValue?>, List<List<PrimitiveRekordValue?>>> = links.groupBy(
                        keySelector = { link -> parentColumns.map { link[it] } },
                        valueTransform = { link -> childColumns.map { link[it] } },
                    )
                    val children = readByIds(childType, linked.values.flatten().distinct())
                    rekords.forEachIndexed { index, rekord ->
                        val items = linked[parentKeys[index]].orEmpty().mapNotNull { children[it] }
                        // A list stored is never told from one written empty, but a list that cannot
                        // be null is read as one, empty or not.
                        rekord[field.name] = if (items.isEmpty() && type.isMarkedNullable) null else RekordValue.CompositeList(items)
                    }
                }
                else -> Unit
            }
        }
        return rekords
    }

    /** The rekords of [rekordType] [keys] identify, by the values of its ids, each as it is read. */
    private suspend fun readByIds(
        rekordType: String,
        keys: List<List<PrimitiveRekordValue?>>,
    ): Map<List<PrimitiveRekordValue?>, RekordValue.Composite> {
        if (keys.isEmpty()) return emptyMap()
        val ids = idFieldNames(rekordType)
        val rows = selectByKeys(rekordType, ids, keys)
        val rekords = read(rekordType, rows, fields = null)
        return rows.indices.associate { index ->
            ids.map { rows[index][it] } to RekordValue.Composite(rekordType, rekords[index])
        }
    }

    /** The rows of [tableName] whose [columns] hold one of [keys]. */
    private suspend fun selectByKeys(
        tableName: String,
        columns: List<String>,
        keys: List<List<PrimitiveRekordValue?>>,
        orderBy: List<Order>? = null,
    ): List<PrimitiveRekordValues> = buildList {
        forEachKeyChunk(columns, keys) { chunk ->
            addAll(connection().select(tableName, columnNames = null, chunk.toSQLCondition(tableName), orderBy))
        }
    }

    /**
     * Runs [action] with a filter selecting the rows whose [columns] hold one of [keys], a chunk of
     * them at a time so that no statement holds more variables than SQLite takes - 999 before its
     * version 3.32.
     */
    private suspend fun forEachKeyChunk(
        columns: List<String>,
        keys: List<List<PrimitiveRekordValue?>>,
        action: suspend (Filter) -> Unit,
    ) {
        if (keys.isEmpty() || columns.isEmpty()) return
        for (chunk in keys.chunked(maxOf(1, MAX_VARIABLES / columns.size))) {
            val filter = if (columns.size == 1) {
                Filter.InList(columns.single(), chunk.map { it.single() })
            } else {
                Filter.Or(chunk.map { key -> Filter.And(columns.zip(key) { column, value -> Filter.Equals(column, value) }) })
            }
            action(filter)
        }
    }

    private fun coercePrimitive(value: PrimitiveRekordValue, ktype: KType): PrimitiveRekordValue =
        when (ktype.classifier) {
            Int::class -> (value as? Number)?.toInt() ?: value
            Long::class -> (value as? Number)?.toLong() ?: value
            Float::class -> (value as? Number)?.toFloat() ?: value
            Double::class -> (value as? Number)?.toDouble() ?: value
            Boolean::class -> when (value) {
                is Boolean -> value
                is Number -> value.toLong() != 0L
                else -> value
            }
            else -> value
        }

    override suspend fun count(rekordType: String, filter: Filter?): Int =
        connection().count(rekordType, filter?.toCondition(rekordType))

    private fun conditionBuilder(): SQLConditionBuilder = SQLConditionBuilder(schemaOf = ::rekordSchema)

    private fun Filter.toCondition(rekordType: String): SQLCondition =
        conditionBuilder().build(this, rekordType, alias = rekordType)

    private fun idFieldNames(rekordType: String): List<String> = conditionBuilder().idFieldNames(rekordType)

    private companion object {

        /** Fewer variables than the 999 a statement of SQLite before 3.32 takes. */
        const val MAX_VARIABLES = 900

        /** The order rows were inserted in, which is that of the rowid a table has by default. */
        val INSERTION_ORDER = listOf(Order.Ascending("rowid"))
    }
}
