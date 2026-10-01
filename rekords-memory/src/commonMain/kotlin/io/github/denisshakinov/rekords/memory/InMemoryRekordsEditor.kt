@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.core.FieldWithType
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.MutableRekordValues
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.RekordValue
import io.github.denisshakinov.rekords.core.RekordValues
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsSchemaEditor
import io.github.denisshakinov.rekords.core.compareTo
import io.github.denisshakinov.rekords.core.field
import io.github.denisshakinov.rekords.core.matches
import io.github.denisshakinov.rekords.core.pinnedValues
import io.github.denisshakinov.rekords.core.rekordSchema
import io.github.denisshakinov.rekords.core.runOnEditor
import io.github.denisshakinov.rekords.core.toStoredValue
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * A [RekordsEditor] keeping rekords in memory.
 *
 * Every rekord type is a table of its own, and a rekord within it is filed under the values of its
 * id fields, so that the rekord those ids name is reached by a lookup instead of a walk over the
 * table. A filter that leaves an id field free is narrowed instead by an index, one per field the
 * schema marks as an id or as searchable, and only what neither answers is scanned for.
 *
 * All of that is what the schema says, which the store hands over - an editor used without one
 * goes on working, with every rekord only ever found by a scan.
 *
 * A transaction is rolled back through a [Journal] of what it changed, so that it costs what it
 * changes rather than what is stored.
 *
 * A migration changes the rekords stored the way the other editors change their storage: nothing
 * outlives the process, but one editor is free to be given to a store of a newer schema version,
 * which then upgrades what it holds - as a test of a migration does.
 */
class InMemoryRekordsEditor : RekordsEditor {

    private val journal = Journal()

    private val tables: MutableMap<String, RekordTable> = mutableMapOf()

    @InternalRekordsApi
    override val schemaEditor: RekordsSchemaEditor = object : RekordsSchemaEditor() {
        private var version: Int = 0
        override suspend fun version(): Int = version
        override suspend fun updateVersion(newVersion: Int) {
            val oldVersion = version
            journal.record { version = oldVersion }
            version = newVersion
        }

        /** A table is created by the first rekord of its type, so there is nothing to make ahead. */
        override suspend fun addRekordType(rekordClass: KClass<*>) {}

        /**
         * Drops the rekords of [rekordType], and what refers to them with them: a parent no longer
         * holds a rekord of it, nor has one in its lists, as a storage looking them up finds none.
         */
        override suspend fun removeRekordType(rekordType: String) {
            val removed = tables.remove(rekordType) ?: return
            journal.record { tables[rekordType] = removed }
            tables.values.forEach { table ->
                table.alter { values -> values.replaceComposites(rekordType) { null } }
            }
        }

        /**
         * Files the rekords of [oldRekordType] under [newRekordType], and has every parent refer to
         * them as rekords of it - which the type a nested rekord is read with is taken from.
         */
        override suspend fun renameRekordType(oldRekordType: String, newRekordType: String) {
            val renamed = tables.remove(oldRekordType) ?: return
            val replaced = tables.put(newRekordType, renamed)
            journal.record {
                tables.remove(newRekordType)
                replaced?.let { tables[newRekordType] = it }
                tables[oldRekordType] = renamed
            }
            restructure(newRekordType) {}
            tables.values.forEach { table ->
                table.alter { values ->
                    values.replaceComposites(oldRekordType) { RekordValue.Composite(newRekordType, it.values) }
                }
            }
        }

        /**
         * Gives every rekord of [rekordType] the field [field], holding [defaultValue] - or, when
         * that is null, the value of the field's type that stands for none, or no value for a
         * nullable one. A rekord holding the field already keeps what it holds.
         */
        override suspend fun addField(
            rekordType: String,
            field: FieldWithType,
            defaultValue: PrimitiveRekordValue?,
        ) {
            val (annotation, type) = field
            val storedValue = defaultValue.toStoredValue()
            require(storedValue == null || type.holds(storedValue)) {
                "$defaultValue cannot be the default value of ${annotation.name} in $rekordType"
            }
            val fillValue = storedValue ?: if (type.isMarkedNullable) null else type.noneValue()
            restructure(rekordType) { values ->
                if (annotation.name !in values) values[annotation.name] = fillValue?.let { RekordValue.Primitive(it) }
            }
        }

        override suspend fun removeField(rekordType: String, fieldName: String) =
            restructure(rekordType) { values -> values.remove(fieldName) }

        override suspend fun renameField(
            rekordType: String,
            oldFieldName: String,
            newFieldName: String
        ) = restructure(rekordType) { values ->
            if (oldFieldName in values) values[newFieldName] = values.remove(oldFieldName)
        }
    }

    /** Nothing is held open, and the rekords stored outlive the call the way a file would. */
    override fun close() = Unit

    /**
     * Runs [action] through [runOnEditor] rather than calling it: the store hands over classes
     * implementing the function type, which Kotlin/JS cannot call as it calls a lambda.
     */
    override suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): T {
        journal.open()
        try {
            return runOnEditor(this, action)
        } catch (e: Throwable) {
            journal.rollBack()
            throw e
        } finally {
            journal.close()
        }
    }

    private fun table(rekordType: String): RekordTable {
        return tables.getOrPut(rekordType) {
            val (idFields, indexedFields) = structure(rekordType)
            RekordTable(journal = journal, idFields = idFields, indexedFields = indexedFields)
        }
    }

    /** The fields the rekords of [rekordType] are filed under, and those they are indexed by. */
    private fun structure(rekordType: String): Pair<List<String>, List<String>> {
        val fields: Collection<FieldWithType> = fields(rekordType)
        val idFields: List<String> = fields.filter { it.field.id }.map { it.field.name }
        val searchableFields: List<String> = fields.filter { it.field.searchable }.map { it.field.name }
        // A single id field is what the key already is, so indexing it would answer nothing the
        // key does not. Several are each worth an index, for the filters that name some of them
        // and leave the rest free.
        return idFields to (idFields.takeIf { it.size > 1 }.orEmpty() + searchableFields).distinct()
    }

    /**
     * Applies [change] to the rekords of [rekordType], filed and indexed afterwards as the schema
     * the migration runs for has them. A type no rekord has been stored of has nothing to change.
     */
    private fun restructure(rekordType: String, change: (MutableRekordValues) -> Unit) {
        val table = tables[rekordType] ?: return
        val (idFields, indexedFields) = structure(rekordType)
        table.alter(idFields, indexedFields, change)
    }

    /**
     * The fields of [rekordType], read once per type: the schema answers by walking the rekord
     * types it declares and reflecting over the one that matches.
     */
    private fun fields(rekordType: String): Collection<FieldWithType> {
        // Only a store sets a schema, so an editor reached on its own has no fields to read and
        // keeps its rekords for scanning alone.
        if (!schemaEditor.hasSchema) {
            return emptyList()
        }
        return rekordSchema(rekordType).values
    }

    override suspend fun put(rekordType: String, values: RekordValues, filter: Filter?) {
        table(rekordType).put(values.normalize(), filter)
    }

    private fun RekordValues.normalize(): MutableRekordValues =
        entries.associateTo(mutableMapOf()) { (key, value) ->
            key to when (value) {
                is RekordValue.Composite -> resolveComposite(value)
                is RekordValue.CompositeList -> RekordValue.CompositeList(value.list.map {
                    resolveComposite(
                        it
                    )
                })
                else -> value
            }
        }

    /**
     * Files the [composite] under its own ids and hands back the rekord stored there, which is the
     * one already filed under them where there is one. Parents hold that rekord itself rather than
     * a copy, which is how a change to it is seen by every one of them.
     */
    private fun resolveComposite(composite: RekordValue.Composite): RekordValue.Composite =
        RekordValue.Composite(
            composite.rekordType,
            table(composite.rekordType).upsert(composite.values.normalize()),
        )

    override suspend fun delete(rekordType: String, filter: Filter?) {
        table(rekordType).delete(filter)
    }

    override suspend fun query(
        rekordType: String,
        fields: List<String>?,
        filter: Filter?,
        orderBy: List<Order>?,
        limit: Int?,
        offset: Int?,
    ): List<RekordValues> {
        var rekords: List<RekordValues> = table(rekordType).select(filter)
        if (orderBy != null) {
            rekords = rekords.sortedWith { values1: RekordValues, values2: RekordValues ->
                values1.compareTo(values2, orderBy)
            }
        }
        if (offset != null) {
            rekords = rekords.drop(offset)
        }
        if (limit != null) {
            rekords = rekords.take(limit)
        }
        return rekords
    }

    override suspend fun count(rekordType: String, filter: Filter?): Int =
        table(rekordType).count(filter)
}

/**
 * The rekords of one rekord type, each filed under the key its [idFields] make and listed in an
 * index for each of [indexedFields].
 *
 * Keys are held in the order they were first stored in, which is the order rekords are handed back
 * in when nothing is asked to be ordered by. A rekord an update files anew goes to the back of
 * that order, so it is the order of a query that asks for none, not one it can count on.
 */
private class RekordTable(
    private val journal: Journal,
    idFields: List<String>,
    indexedFields: List<String>,
) {

    private val rekords: MutableMap<RekordKey, MutableRekordValues> = mutableMapOf()

    /** The fields the rekords are filed under, which a migration may change. */
    var idFields: List<String> = idFields
        private set

    /** For each indexed field, the keys of the rekords holding a given value of it. */
    private var indexes: Map<String, MutableMap<PrimitiveRekordValue?, MutableSet<RekordKey>>> =
        indexedFields.associateWith { mutableMapOf() }

    /** The fields an index is kept for. */
    val indexedFields: List<String> get() = indexes.keys.toList()

    /** Numbers the rekords no ids can be read from, so that each is kept rather than merged. */
    private var keylessCount: Long = 0

    /**
     * Applies [values] to the rekords [filter] selects, and stores them as a rekord of their own
     * when it selects none - which is where an insert comes from, ids and all.
     */
    fun put(values: MutableRekordValues, filter: Filter?) {
        // Selecting nothing in particular selects every rekord, as an UPDATE with no WHERE does.
        val selected = if (filter == null) rekords.keys.toList() else selectKeys(filter)
        if (selected.isEmpty()) {
            // A filter that selected nothing is a write about the one rekord it describes, which
            // is then stored. A write about every rekord of the type stores none.
            if (filter != null) {
                insertIfAbsent(values)
            }
            return
        }
        for (key in selected) {
            val stored = rekords[key] ?: continue
            update(key, stored, values)
        }
    }

    /**
     * Files [values] under the ids they carry and returns the rekord stored there: the one already
     * filed under those ids, having taken the values given, or the values themselves.
     */
    fun upsert(values: MutableRekordValues): MutableRekordValues {
        val key = keyOf(values) ?: return values.also { addKeyless(it) }
        val stored = rekords[key]
        if (stored == null) {
            insert(key, values)
            return values
        }
        update(key, stored, values)
        return stored
    }

    fun delete(filter: Filter?) {
        if (filter == null) {
            val removed = rekords.toMap()
            journal.record {
                removed.forEach { (key, values) -> insert(key, values) }
            }
            rekords.clear()
            indexes.values.forEach { it.clear() }
            return
        }
        selectKeys(filter).forEach { remove(it) }
    }

    fun select(filter: Filter?): List<RekordValues> {
        if (filter == null) {
            return rekords.values.toList()
        }
        val narrowed = narrow(filter) ?: return rekords.values.filter { it.matches(filter) }
        return narrowed.mapNotNull { key -> rekords[key]?.takeIf { it.matches(filter) } }
    }

    fun count(filter: Filter?): Int {
        if (filter == null) {
            return rekords.size
        }
        val narrowed = narrow(filter) ?: return rekords.values.count { it.matches(filter) }
        return narrowed.count { key -> rekords[key]?.matches(filter) == true }
    }

    /**
     * Applies [change] to every rekord, in the very map stored - which the parents holding it hold
     * too - and files them anew under [idFields], indexed by [indexedFields]: what a migration
     * does, changing the fields either is drawn from as well as the rekords.
     *
     * Ids two rekords come to share leave the one filed last, as an update does. A rekord no ids
     * are read from keeps the key it had.
     */
    fun alter(
        idFields: List<String>,
        indexedFields: List<String>,
        change: (MutableRekordValues) -> Unit,
    ) {
        val previousEntries: List<Pair<RekordKey, MutableRekordValues>> = rekords.map { (key, values) -> key to values }
        val previousValues: List<RekordValues>? = if (journal.isOpen) previousEntries.map { (_, values) -> values.toMap() } else null
        val previousIdFields = this.idFields
        val previousIndexedFields = this.indexedFields
        previousEntries.forEach { (_, values) -> change(values) }
        refile(idFields, indexedFields, previousEntries)
        if (previousValues != null) {
            journal.record {
                previousEntries.forEachIndexed { index, (_, values) ->
                    values.clear()
                    values.putAll(previousValues[index])
                }
                this.idFields = previousIdFields
                rekords.clear()
                indexes = previousIndexedFields.associateWith { mutableMapOf() }
                previousEntries.forEach { (key, values) ->
                    rekords[key] = values
                    index(key, values)
                }
            }
        }
    }

    /** Applies [change] to every rekord, leaving what they are filed under and indexed by as it is. */
    fun alter(change: (MutableRekordValues) -> Unit) = alter(idFields, indexedFields, change)

    private fun refile(
        idFields: List<String>,
        indexedFields: List<String>,
        entries: List<Pair<RekordKey, MutableRekordValues>>,
    ) {
        this.idFields = idFields
        rekords.clear()
        indexes = indexedFields.associateWith { mutableMapOf() }
        for ((key, values) in entries) {
            rekords[keyOf(values) ?: key.takeIf { it is RekordKey.Ordinal } ?: RekordKey.Ordinal(keylessCount++)] = values
        }
        rekords.forEach { (key, values) -> index(key, values) }
    }

    /**
     * The keys of the rekords [filter] selects, read before anything is written: what a write does
     * to a rekord can move it, and the keys are walked while that happens.
     */
    private fun selectKeys(filter: Filter): List<RekordKey> =
        (narrow(filter) ?: rekords.keys)
            .filter { key -> rekords[key]?.matches(filter) == true }

    /**
     * The keys [filter] leaves as possible, or null when nothing narrows them - the caller then
     * has every rekord to look through.
     *
     * The ids come first: a filter that pins all of them names a single rekord. Short of that,
     * each indexed field the filter pins offers the keys holding those values, and the fewest of
     * them are taken - what a filter asks beyond that is left to [predicate], which the rekords
     * found still have to satisfy.
     */
    private fun narrow(filter: Filter): Collection<RekordKey>? {
        val pinned: Map<String, List<PrimitiveRekordValue?>> = filter.pinnedValues()
        if (pinned.isEmpty()) {
            return null
        }
        pinnedKey(pinned)?.let { return listOf(it) }
        var narrowest: Collection<RekordKey>? = null
        for ((fieldName, values) in pinned) {
            val index = indexes[fieldName] ?: continue
            val keys = values.flatMapTo(mutableSetOf()) { value -> index[value].orEmpty() }
            if (narrowest == null || keys.size < narrowest.size) {
                narrowest = keys
            }
        }
        return narrowest
    }

    /**
     * Writes [values] into [stored] and leaves the key and the indexes true to what it holds
     * afterwards, an update being free to have changed the fields either is drawn from.
     *
     * Ids another rekord already holds take its place: nothing here keeps an update from making
     * one rekord out of two, the way a primary key would.
     */
    private fun update(key: RekordKey, stored: MutableRekordValues, values: MutableRekordValues) {
        val previousValues: RekordValues? = if (journal.isOpen) stored.toMap() else null
        unindex(key, stored)
        stored.putAll(values)
        val currentKey = keyOf(stored) ?: key
        var displaced: MutableRekordValues? = null
        if (currentKey != key) {
            rekords.remove(key)
            displaced = rekords.put(currentKey, stored)?.also { unindex(currentKey, it) }
        }
        index(currentKey, stored)
        if (previousValues != null) {
            journal.record {
                // Taken back into the same map, since every parent holding it holds that map.
                unindex(currentKey, stored)
                stored.clear()
                stored.putAll(previousValues)
                if (currentKey != key) {
                    rekords.remove(currentKey)
                    displaced?.let { insert(currentKey, it) }
                    rekords[key] = stored
                }
                index(key, stored)
            }
        }
    }

    /**
     * Stores [values] as a rekord unless one is filed under the same ids already, which is left as
     * it is - what an `INSERT OR IGNORE` does.
     */
    private fun insertIfAbsent(values: MutableRekordValues) {
        val key = keyOf(values) ?: return addKeyless(values)
        if (!rekords.containsKey(key)) {
            insert(key, values)
        }
    }

    private fun addKeyless(values: MutableRekordValues) {
        insert(RekordKey.Ordinal(keylessCount++), values)
    }

    private fun insert(key: RekordKey, values: MutableRekordValues) {
        journal.record { remove(key) }
        rekords[key] = values
        index(key, values)
    }

    private fun remove(key: RekordKey) {
        val removed = rekords.remove(key) ?: return
        journal.record { insert(key, removed) }
        unindex(key, removed)
    }

    private fun index(key: RekordKey, values: RekordValues) {
        for ((fieldName, index) in indexes) {
            index.getOrPut(values.primitive(fieldName)) { mutableSetOf() }.add(key)
        }
    }

    private fun unindex(key: RekordKey, values: RekordValues) {
        for ((fieldName, index) in indexes) {
            val value = values.primitive(fieldName)
            val keys = index[value] ?: continue
            keys.remove(key)
            if (keys.isEmpty()) {
                index.remove(value)
            }
        }
    }

    /**
     * The key [values] belong under, or null when no ids can be read from them - the type has none
     * declared, or the values carry none of them.
     */
    private fun keyOf(values: RekordValues): RekordKey.Ids? {
        if (idFields.isEmpty()) {
            return null
        }
        return RekordKey.Ids(idFields.map { fieldName ->
            if (!values.containsKey(fieldName)) return null
            values.primitive(fieldName)
        })
    }

    /**
     * The key the [pinned] values name, or null when they leave an id field free or hold it to
     * more than one value.
     */
    private fun pinnedKey(pinned: Map<String, List<PrimitiveRekordValue?>>): RekordKey.Ids? {
        if (idFields.isEmpty()) {
            return null
        }
        return RekordKey.Ids(idFields.map { fieldName ->
            val values = pinned[fieldName] ?: return null
            if (values.size != 1) return null
            values.first()
        })
    }
}

/** What a rekord is filed under within a [RekordTable]. */
private sealed interface RekordKey {

    /** The values of the id fields, in the order the schema declares them in. */
    data class Ids(val values: List<PrimitiveRekordValue?>) : RekordKey

    /** Stands for a rekord no ids could be read from: one of a kind, and only found by a scan. */
    data class Ordinal(val ordinal: Long) : RekordKey
}

private fun RekordValues.primitive(fieldName: String): PrimitiveRekordValue? =
    (this[fieldName] as? RekordValue.Primitive)?.value

/**
 * Replaces each rekord of [rekordType] these values hold with what [replacement] makes of it - in
 * a field of its own, where null leaves the field holding none, or in a list, where null drops it.
 */
private fun MutableRekordValues.replaceComposites(
    rekordType: String,
    replacement: (RekordValue.Composite) -> RekordValue.Composite?,
) {
    for ((fieldName, value) in entries.toList()) {
        when (value) {
            is RekordValue.Composite -> if (value.rekordType == rekordType) this[fieldName] = replacement(value)
            is RekordValue.CompositeList -> if (value.list.any { it.rekordType == rekordType }) {
                this[fieldName] = RekordValue.CompositeList(
                    value.list.mapNotNull { if (it.rekordType == rekordType) replacement(it) else it }
                )
            }
            else -> Unit
        }
    }
}

/**
 * Whether [value], encoded the way a rekord's fields are, is one a field of this type holds - the
 * way a SQL column of the type tells.
 */
private fun KType.holds(value: PrimitiveRekordValue): Boolean = when (classifier) {
    Int::class, Long::class, Boolean::class -> value is Int || value is Long || value is Boolean
    Float::class, Double::class -> value is Float || value is Double
    String::class -> value is String
    else -> false
}

/** The value of this type that stands for none. */
private fun KType.noneValue(): PrimitiveRekordValue = when (classifier) {
    Int::class -> 0
    Long::class -> 0L
    Float::class -> 0f
    Double::class -> 0.0
    Boolean::class -> false
    String::class -> ""
    else -> throw IllegalArgumentException("No value stands for none of $this")
}

/**
 * What the transaction running has changed, kept as the steps that take each change back.
 *
 * Rolling back takes them in the reverse of the order they were made in, so that each finds things
 * the way its change left them. What is restored is the rekords and what indexes them; the order
 * rekords are handed back in when a query asks for none is not, being no order to count on.
 */
private class Journal {

    private val undoSteps: ArrayDeque<() -> Unit> = ArrayDeque()

    /** Whether a transaction is running, which is the only time changes are recorded. */
    var isOpen: Boolean = false
        private set

    fun open() {
        isOpen = true
    }

    /**
     * Keeps [undo] as what takes back the change about to be made. Taking it back changes things
     * as well, which is not recorded: the journal is only ever rolled back once.
     */
    fun record(undo: () -> Unit) {
        if (isOpen) {
            undoSteps.addLast(undo)
        }
    }

    fun rollBack() {
        isOpen = false
        while (undoSteps.isNotEmpty()) {
            undoSteps.removeLast().invoke()
        }
    }

    fun close() {
        isOpen = false
        undoSteps.clear()
    }
}
