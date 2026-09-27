@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.FieldFilter
import io.github.denisshakinov.rekords.core.FieldValueFilter
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
import io.github.denisshakinov.rekords.core.field
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
        val primitiveValues = buildMap {
            for ((fieldName, value) in values) {
                when (value) {
                    null, is RekordValue.Primitive -> put(fieldName, value?.value)
                    is RekordValue.Composite ->
                        rekordSchema(value.rekordType).values
                            .filter { it.field.id }
                            .forEach { (childField, _) ->
                                put(
                                    "${fieldName}_${childField.name}",
                                    (value.values[childField.name] as? RekordValue.Primitive)?.value
                                )
                            }
                    is RekordValue.CompositeList -> {}
                }
            }
        }
        connection().upsert(rekordType, primitiveValues, filter)
        for ((fieldName, value) in values) {
            when (value) {
                is RekordValue.Composite -> {
                    val f = buildIdFilter(value.rekordType, value.values)
                    upsertWithComposites(value.rekordType, value.values, f)
                }
                is RekordValue.CompositeList -> {
                    val junctionTable = "${rekordType}_${fieldName}"
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
                        // already there - only one that is not to insert.
                        connection().insertOrIgnore(
                            junctionTable,
                            parentIdValues + prefixedIdValues(item.rekordType, item.values),
                        )
                    }
                }
                else -> {}
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
        connection().delete(junctionTable, buildFilter(parentIdValues))
    }

    // Extracts id field values prefixed with rekordType to avoid column name collisions in junction tables.
    // e.g. Game(id=1) -> {"Game_id": 1}
    private fun prefixedIdValues(rekordType: String, values: RekordValues): PrimitiveRekordValues =
        rekordSchema(rekordType).values
            .filter { it.field.id }
            .associate { "${rekordType}_${it.field.name}" to (values[it.field.name] as? RekordValue.Primitive)?.value }

    private fun buildIdFilter(rekordType: String, values: RekordValues): Filter? =
        buildFilter(
            rekordSchema(rekordType).values
                .filter { it.field.id }
                .associate { it.field.name to (values[it.field.name] as? RekordValue.Primitive)?.value }
        )

    private fun buildFilter(values: PrimitiveRekordValues): Filter? {
        val filters = values.mapNotNull { (name, v) -> v?.let { Filter.Equals(name, it) } }
        return when (filters.size) {
            0 -> null
            1 -> filters.first()
            else -> Filter.And(filters)
        }
    }

    override suspend fun delete(rekordType: String, filter: Filter?) {
        rekordSchema(rekordType).values
            .filter { it.type is RekordListKType }
            .forEach { (fieldAnnotation, _) ->
                connection().delete(
                    "${rekordType}_${fieldAnnotation.name}",
                    filter?.translateFieldNames { "${rekordType}_$it" }
                )
            }
        connection().delete(rekordType, filter)
    }

    private fun Filter.translateFieldNames(transform: (String) -> String): Filter = when (this) {
        is Filter.And -> Filter.And(filters.map { it.translateFieldNames(transform) })
        is Filter.Or -> Filter.Or(filters.map { it.translateFieldNames(transform) })
        is Filter.Not -> Filter.Not(filter.translateFieldNames(transform) as FieldFilter)
        is FieldFilter.Nested -> Filter.Nested(transform(field), filter)
        is FieldFilter.InList -> Filter.InList(transform(field), list)
        is FieldFilter.Contains -> Filter.Contains(transform(field), value)
        is FieldValueFilter.Equals -> Filter.Equals(transform(field), value)
        is FieldValueFilter.LessThan -> Filter.LessThan(transform(field), value)
        is FieldValueFilter.LessThanOrEquals -> Filter.LessThanOrEquals(transform(field), value)
        is FieldValueFilter.GreaterThan -> Filter.GreaterThan(transform(field), value)
        is FieldValueFilter.GreaterThanOrEquals -> Filter.GreaterThanOrEquals(transform(field), value)
    }

    private fun Filter.resolveNested(
        rekordType: String,
        joins: MutableList<SqlJoin>,
    ): Filter = when (this) {
        is Filter.And -> Filter.And(filters.map { it.resolveNested(rekordType, joins) })
        is Filter.Or -> Filter.Or(filters.map { it.resolveNested(rekordType, joins) })
        is Filter.Not -> Filter.Not(filter.resolveNested(rekordType, joins) as FieldFilter)
        is FieldFilter.Nested -> {
            when (val ktype = rekordSchema(rekordType)[field]?.type) {
                is RekordKType -> {
                    val childType = ktype.rekordType()
                    val childIds = rekordSchema(childType).values.filter { it.field.id }.map { it.field.name }
                    if (joins.none { it.table == childType }) {
                        joins.add(SqlJoin(
                            table = childType,
                            onCondition = childIds.joinToString(" AND ") { c ->
                                "$childType.$c = $rekordType.${field}_$c"
                            }
                        ))
                    }
                    filter.qualify(childType)
                }
                is RekordListKType -> {
                    val childType = ktype.elementRekordType()
                    val childIds = rekordSchema(childType).values.filter { it.field.id }.map { it.field.name }
                    val parentIds = rekordSchema(rekordType).values.filter { it.field.id }.map { it.field.name }
                    val junction = "${rekordType}_${field}"
                    if (joins.none { it.table == junction }) {
                        joins.add(SqlJoin(
                            table = junction,
                            onCondition = parentIds.joinToString(" AND ") { p ->
                                "$junction.${rekordType}_$p = $rekordType.$p"
                            }
                        ))
                    }
                    if (joins.none { it.table == childType }) {
                        joins.add(SqlJoin(
                            table = childType,
                            onCondition = childIds.joinToString(" AND ") { c ->
                                "$childType.$c = $junction.${childType}_$c"
                            }
                        ))
                    }
                    filter.qualify(childType)
                }
                else -> this
            }
        }
        else -> this
    }

    override suspend fun query(
        rekordType: String,
        fields: List<String>?,
        filter: Filter?,
        orderBy: List<Order>?,
        limit: Int?,
        offset: Int?,
    ): List<RekordValues> {
        val (columnNames, schemaJoins) = buildSelectSpec(rekordType, fields)
        val nestedJoins = mutableListOf<SqlJoin>()
        val resolvedFilter = filter?.resolveNested(rekordType, nestedJoins)
        val allJoins = (schemaJoins + nestedJoins).distinctBy { it.table }
        return connection()
            .select(rekordType, columnNames, allJoins, resolvedFilter?.qualify(rekordType), orderBy, limit, offset)
            .toRekordValues()
            .map { coerceTypes(it, rekordType) }
    }

    private fun coerceTypes(values: RekordValues, rekordType: String): RekordValues {
        val schema = rekordSchema(rekordType)
        if (schema.isEmpty()) return values
        return values.mapValues { (fieldName, value) ->
            when (value) {
                null -> null
                is RekordValue.Primitive -> {
                    val ktype = schema[fieldName]?.type
                        ?: return@mapValues value
                    RekordValue.Primitive(coercePrimitive(value.value, ktype))
                }
                is RekordValue.Composite ->
                    RekordValue.Composite(
                        value.rekordType,
                        coerceTypes(value.values, value.rekordType)
                    )
                is RekordValue.CompositeList ->
                    RekordValue.CompositeList(value.list.map { item ->
                        RekordValue.Composite(
                            item.rekordType,
                            coerceTypes(item.values, item.rekordType)
                        )
                    })
            }
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

    private fun buildSelectSpec(
        rekordType: String,
        fields: List<String>?,
    ): Pair<List<String>?, List<SqlJoin>> {
        val schema = rekordSchema(rekordType)
        if (schema.isEmpty()) return fields to emptyList()

        val parentIds = schema.values.filter { it.field.id }.map { it.field.name }
        val columnNames = mutableListOf<String>()
        val joins = mutableListOf<SqlJoin>()

        for ((fieldAnnotation, ktype) in schema.values) {
            when {
                ktype is RekordKType -> {
                    val childType = ktype.rekordType()
                    val childSchema = rekordSchema(childType)
                    val childIds = childSchema.values.filter { it.field.id }.map { it.field.name }
                    joins.add(
                        SqlJoin(
                            table = childType,
                            onCondition = childIds.joinToString(" AND ") { c ->
                                "$childType.$c = $rekordType.${fieldAnnotation.name}_$c"
                            },
                        )
                    )
                    childSchema.values
                        .filter { it.type !is RekordKType && it.type !is RekordListKType }
                        .forEach { (childField, _) ->
                            columnNames.add(
                                "$childType.${childField.name} AS \"${fieldAnnotation.name}$SEP${childType}$SEP${childField.name}\""
                            )
                        }
                }
                ktype is RekordListKType -> {
                    val childType = ktype.elementRekordType()
                    val childSchema = rekordSchema(childType)
                    val childIds = childSchema.values.filter { it.field.id }.map { it.field.name }
                    val junction = "${rekordType}_${fieldAnnotation.name}"
                    joins.add(
                        SqlJoin(
                            table = junction,
                            onCondition = parentIds.joinToString(" AND ") { p ->
                                "$junction.${rekordType}_$p = $rekordType.$p"
                            },
                        )
                    )
                    joins.add(
                        SqlJoin(
                            table = childType,
                            onCondition = childIds.joinToString(" AND ") { c ->
                                "$childType.$c = $junction.${childType}_$c"
                            },
                        )
                    )
                    childSchema.values
                        .filter { it.type !is RekordKType && it.type !is RekordListKType }
                        .forEach { (childField, _) ->
                            columnNames.add(
                                "$childType.${childField.name} AS \"${fieldAnnotation.name}$LIST_SUFFIX$SEP${childType}$SEP${childField.name}\""
                            )
                        }
                }
                else -> if (fields == null || fieldAnnotation.name in fields) {
                    columnNames.add("$rekordType.${fieldAnnotation.name} AS ${fieldAnnotation.name}")
                }
            }
        }
        return columnNames.takeIf { it.isNotEmpty() } to joins
    }

    override suspend fun count(rekordType: String, filter: Filter?): Int {
        val nestedJoins = mutableListOf<SqlJoin>()
        val resolvedFilter = filter?.resolveNested(rekordType, nestedJoins)
        if (nestedJoins.isEmpty()) return connection().count(rekordType, resolvedFilter)
        return connection().select(
            tableName = rekordType,
            columnNames = listOf("COUNT(*)"),
            joins = nestedJoins,
            filter = resolvedFilter?.qualify(rekordType),
        ).firstIntValue() ?: 0
    }
}