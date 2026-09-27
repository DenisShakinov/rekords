@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.indexeddb

import com.juul.indexeddb.Key
import com.juul.indexeddb.ObjectStore
import com.juul.indexeddb.WriteTransaction
import com.juul.indexeddb.bound
import io.github.denisshakinov.rekords.core.FieldFilter
import io.github.denisshakinov.rekords.core.FieldWithType
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.RekordKType
import io.github.denisshakinov.rekords.core.RekordListKType
import io.github.denisshakinov.rekords.core.RekordValue
import io.github.denisshakinov.rekords.core.RekordValues
import io.github.denisshakinov.rekords.core.compareTo
import io.github.denisshakinov.rekords.core.elementRekordType
import io.github.denisshakinov.rekords.core.field
import io.github.denisshakinov.rekords.core.matches
import io.github.denisshakinov.rekords.core.pinnedValues
import io.github.denisshakinov.rekords.core.rekordType
import io.github.denisshakinov.rekords.core.toStoredValue
import io.github.denisshakinov.rekords.core.type
import kotlin.js.JsAny
import kotlin.reflect.KType

internal const val REKORDS_STORE = "rekords"
internal const val SEARCH_STORE = "search"
internal const val META_STORE = "meta"

private const val VERSION_KEY = "version"
private const val ORDINAL_KEY = "ordinal"

/**
 * One operation of an [IndexedDBRekordsEditor], run within [transaction].
 *
 * Rekords are filed the way the in-memory editor files them, and found the same way: under the
 * key their ids make, through an index for each field the schema marks as searchable - and for
 * each id field where there are several - and by a walk over every rekord of the type for what
 * neither answers. What a filter asks beyond that is told by
 * [matches], the way the in-memory editor tells it.
 *
 * The indexes are entries of the search store, one per indexed field of each rekord, under
 * `[rekordType, field, value, rekordId]`, holding the parts of the rekord's key.
 *
 * A composite is a rekord of its own, which a parent refers to rather than holds, so that a change
 * to it is seen by every parent. It is read back into the parent when the parent is read.
 *
 * @param schema the fields of a rekord type, none for a type the schema does not declare.
 */
internal class RekordsSession(
    private val transaction: WriteTransaction,
    private val schema: (rekordType: String) -> Map<String, FieldWithType>,
) {

    private val rekords: ObjectStore = transaction.objectStore(REKORDS_STORE)
    private val search: ObjectStore = transaction.objectStore(SEARCH_STORE)
    private val meta: ObjectStore = transaction.objectStore(META_STORE)

    /** The composites read back so far, by the id of the rekord each one is. */
    private val composites: MutableMap<String, RekordValue.Composite?> = mutableMapOf()

    suspend fun put(rekordType: String, values: RekordValues, filter: Filter?) {
        val fields = store(values)
        // Selecting nothing in particular selects every rekord, as an UPDATE with no WHERE does.
        val selected = if (filter == null) all(rekordType) else select(rekordType, filter).map { it.rekord }
        if (selected.isEmpty()) {
            // A filter that selected nothing is a write about the one rekord it describes, which
            // is then stored. A write about every rekord of the type stores none.
            if (filter != null) {
                insertIfAbsent(rekordType, fields)
            }
            return
        }
        for (rekord in selected) {
            update(rekord, fields)
        }
    }

    suspend fun delete(rekordType: String, filter: Filter?) {
        if (filter == null) {
            with(transaction) {
                rekords.delete(typeRange(rekordType))
                search.delete(typeRange(rekordType))
            }
            return
        }
        select(rekordType, filter).forEach { remove(it.rekord) }
    }

    suspend fun query(
        rekordType: String,
        filter: Filter?,
        orderBy: List<Order>?,
        limit: Int?,
        offset: Int?,
    ): List<RekordValues> {
        var found: List<RekordValues> = select(rekordType, filter, readComposites = true).map { it.values }
        if (orderBy != null) {
            found = found.sortedWith { values1, values2 -> values1.compareTo(values2, orderBy) }
        }
        if (offset != null) {
            found = found.drop(offset)
        }
        if (limit != null) {
            found = found.take(limit)
        }
        return found
    }

    suspend fun count(rekordType: String, filter: Filter?): Int {
        if (filter == null) {
            return with(transaction) { rekords.count(typeRange(rekordType)) }
        }
        return select(rekordType, filter).size
    }

    suspend fun version(): Int =
        with(transaction) { meta.get(Key(jsString(VERSION_KEY))) }?.let { toKotlinDouble(it).toInt() } ?: 0

    suspend fun updateVersion(newVersion: Int) {
        with(transaction) { meta.put(jsNumber(newVersion.toDouble()), Key(jsString(VERSION_KEY))) }
    }

    suspend fun removeRekordType(rekordType: String) = delete(rekordType, filter = null)

    /**
     * Files every rekord of [oldRekordType] under [newRekordType]. What refers to them is left as
     * it is: a reference holds the parts of the key, the type being the one the schema declares.
     */
    suspend fun renameRekordType(oldRekordType: String, newRekordType: String) {
        val renamed = all(oldRekordType).map { StoredRekord(newRekordType, it.parts, it.fields) }
        delete(oldRekordType, filter = null)
        renamed.forEach { write(it) }
    }

    /**
     * Gives every rekord of [rekordType] the field [field], holding [defaultValue] - or, when that
     * is null, the value of the field's type that stands for none, or no value for a nullable one.
     */
    suspend fun addField(rekordType: String, field: FieldWithType, defaultValue: PrimitiveRekordValue?) {
        val (annotation, type) = field
        val storedValue = defaultValue.toStoredValue()
        require(storedValue == null || type.holds(storedValue)) {
            "$defaultValue cannot be the default value of ${annotation.name} in $rekordType"
        }
        val fillValue = (storedValue ?: if (type.isMarkedNullable) null else type.noneValue()).toStored()
        for (rekord in all(rekordType)) {
            if (annotation.name in rekord.fields) continue
            removeSearchEntries(rekord)
            rekord.fields[annotation.name] = fillValue
            write(rekord)
        }
    }

    suspend fun removeField(rekordType: String, fieldName: String) {
        for (rekord in all(rekordType)) {
            if (fieldName !in rekord.fields) continue
            // The field is gone from the schema, which then no longer tells of its index entry.
            removeSearchEntry(rekord, fieldName)
            removeSearchEntries(rekord)
            rekord.fields.remove(fieldName)
            write(rekord)
        }
    }

    suspend fun renameField(rekordType: String, oldFieldName: String, newFieldName: String) {
        for (rekord in all(rekordType)) {
            if (oldFieldName !in rekord.fields) continue
            removeSearchEntry(rekord, oldFieldName)
            removeSearchEntries(rekord)
            rekord.fields[newFieldName] = rekord.fields.remove(oldFieldName)
            write(rekord)
        }
    }

    /** A rekord [select] found, with the values it holds. */
    private class Selected(val rekord: StoredRekord, val values: RekordValues)

    /**
     * The rekords of [rekordType] [filter] selects, in the order of their keys.
     *
     * Their composites are read back only when [readComposites] asks for them or [filter] looks
     * into one - otherwise nothing tells of them, and reading each is a request of its own.
     */
    private suspend fun select(
        rekordType: String,
        filter: Filter?,
        readComposites: Boolean = false,
    ): List<Selected> {
        val candidates = filter?.let { narrow(rekordType, it) } ?: all(rekordType)
        val withComposites = readComposites || filter?.looksIntoComposites() == true
        return candidates.mapNotNull { rekord ->
            val values = read(rekord, withComposites)
            if (filter == null || values.matches(filter)) Selected(rekord, values) else null
        }
    }

    /**
     * The rekords [filter] leaves as possible, or null when nothing narrows them - every rekord of
     * the type is then to be looked through. The ways to narrow them are tried in the order the
     * in-memory editor tries them in: the key all the ids pin, the keys one id is held to, and the
     * fewest rekords an index offers.
     */
    private suspend fun narrow(rekordType: String, filter: Filter): List<StoredRekord>? {
        val pinned: Map<String, List<PrimitiveRekordValue?>> = filter.pinnedValues()
        if (pinned.isEmpty()) {
            return null
        }
        val idFields = idFields(rekordType)
        if (idFields.isNotEmpty() && idFields.all { pinned[it]?.size == 1 }) {
            val parts = jsArrayOf(idFields.map { keyComponent(pinned.getValue(it).single().toStored()) })
            return listOfNotNull(get(rekordType, parts))
        }
        if (idFields.size == 1) {
            pinned[idFields.single()]?.let { ids ->
                return ids.distinct()
                    .mapNotNull { get(rekordType, jsArrayOf(listOf(keyComponent(it.toStored())))) }
                    .sortedByKey()
            }
        }
        val indexedFields = indexedFields(rekordType)
        var narrowest: List<JsAny>? = null
        for ((fieldName, values) in pinned) {
            if (fieldName !in indexedFields) continue
            val found = values.distinct()
                .flatMap { searchFor(rekordType, fieldName, it) }
                .distinctBy { stringify(it) }
            if (narrowest == null || found.size < narrowest.size) {
                narrowest = found
            }
        }
        return narrowest?.mapNotNull { get(rekordType, it) }?.sortedByKey()
    }

    private suspend fun searchFor(rekordType: String, fieldName: String, value: PrimitiveRekordValue?): List<JsAny> {
        val prefix = listOf(jsString(rekordType), jsString(fieldName), keyComponent(value.toStored()))
        // Every rekord id is a string, and an array orders after any string.
        val range = bound(jsArrayOf(prefix), jsArrayOf(prefix + jsArray()), upperOpen = true)
        return with(transaction) { search.getAll(range) }.elements().filterNotNull()
    }

    private suspend fun all(rekordType: String): List<StoredRekord> =
        with(transaction) { rekords.getAll(typeRange(rekordType)) }
            .elements()
            .filterNotNull()
            .map { StoredRekord.fromJs(rekordType, it) }

    private suspend fun get(rekordType: String, parts: JsAny): StoredRekord? =
        with(transaction) { rekords.get(Key(rekordKey(rekordType, parts))) }
            ?.let { StoredRekord.fromJs(rekordType, it) }

    /** Stores [rekord], with an index entry for each of its indexed fields. */
    private suspend fun write(rekord: StoredRekord) {
        with(transaction) {
            rekords.put(rekord.toJs(), Key(rekord.key))
            for (fieldName in indexedFields(rekord.rekordType)) {
                search.put(rekord.parts, Key(searchKey(rekord, fieldName, rekord.fields[fieldName])))
            }
        }
    }

    private suspend fun remove(rekord: StoredRekord) {
        with(transaction) { rekords.delete(Key(rekord.key)) }
        removeSearchEntries(rekord)
    }

    /** Drops the index entries [rekord] has, as it holds its values now. */
    private suspend fun removeSearchEntries(rekord: StoredRekord) {
        for (fieldName in indexedFields(rekord.rekordType)) {
            removeSearchEntry(rekord, fieldName)
        }
    }

    private suspend fun removeSearchEntry(rekord: StoredRekord, fieldName: String) {
        with(transaction) { search.delete(Key(searchKey(rekord, fieldName, rekord.fields[fieldName]))) }
    }

    private fun searchKey(rekord: StoredRekord, fieldName: String, stored: JsAny?): JsAny = jsArrayOf(
        listOf(jsString(rekord.rekordType), jsString(fieldName), keyComponent(stored), jsString(rekord.id))
    )

    /**
     * Writes [fields] into [rekord] and files it anew where that changed the ids it is filed
     * under - taking the place of another rekord holding them, as the in-memory editor does.
     */
    private suspend fun update(rekord: StoredRekord, fields: Map<String, JsAny?>) {
        val merged = (rekord.fields + fields).toMutableMap()
        val parts = keyParts(rekord.rekordType, merged) ?: rekord.parts
        val updated = StoredRekord(rekord.rekordType, parts, merged)
        remove(rekord)
        if (updated.id != rekord.id) {
            get(updated.rekordType, parts)?.let { displaced -> removeSearchEntries(displaced) }
        }
        write(updated)
    }

    /**
     * Stores [fields] as a rekord unless one is filed under the same ids already, which is left as
     * it is - what an `INSERT OR IGNORE` does.
     */
    private suspend fun insertIfAbsent(rekordType: String, fields: MutableMap<String, JsAny?>) {
        val parts = keyParts(rekordType, fields)
        if (parts == null) {
            insertKeyless(rekordType, fields)
        } else if (get(rekordType, parts) == null) {
            write(StoredRekord(rekordType, parts, fields))
        }
    }

    /**
     * Files the composite of [rekordType] holding [values] under its own ids, and returns the parts
     * of its key. A composite already filed under them takes the values given.
     */
    private suspend fun upsert(rekordType: String, values: RekordValues): JsAny {
        val fields = store(values)
        val parts = keyParts(rekordType, fields) ?: return insertKeyless(rekordType, fields)
        val stored = get(rekordType, parts)
        if (stored == null) {
            write(StoredRekord(rekordType, parts, fields))
        } else {
            update(stored, fields)
        }
        return parts
    }

    /** Stores [fields] as a rekord no ids can be read from, one of a kind, and returns its parts. */
    private suspend fun insertKeyless(rekordType: String, fields: MutableMap<String, JsAny?>): JsAny {
        val ordinal = with(transaction) { meta.get(Key(jsString(ORDINAL_KEY))) }?.let(::toKotlinDouble) ?: 0.0
        with(transaction) { meta.put(jsNumber(ordinal + 1), Key(jsString(ORDINAL_KEY))) }
        val parts = jsArrayOf(listOf(jsArrayOf(listOf(jsNumber(ordinal)))))
        write(StoredRekord(rekordType, parts, fields))
        return parts
    }

    /**
     * [values] as a rekord holds them, a composite being filed first - under its own ids - and
     * held as the parts of its key.
     */
    private suspend fun store(values: RekordValues): MutableMap<String, JsAny?> {
        val fields: MutableMap<String, JsAny?> = mutableMapOf()
        for ((fieldName, value) in values) {
            fields[fieldName] = when (value) {
                null -> null
                is RekordValue.Primitive -> value.value.toStored()
                is RekordValue.Composite -> upsert(value.rekordType, value.values)
                is RekordValue.CompositeList -> jsArrayOf(value.list.map { upsert(it.rekordType, it.values) })
            }
        }
        return fields
    }

    /**
     * The values [rekord] holds, each as the type the schema declares it with. A composite is read
     * back only [withComposites], and is otherwise left out.
     */
    private suspend fun read(rekord: StoredRekord, withComposites: Boolean): RekordValues {
        val fields = schema(rekord.rekordType)
        val values: MutableMap<String, RekordValue?> = mutableMapOf()
        for ((fieldName, stored) in rekord.fields) {
            val type: KType? = fields[fieldName]?.type
            values[fieldName] = when {
                stored == null -> null
                type is RekordKType -> if (withComposites) composite(type.rekordType(), stored) else null
                type is RekordListKType -> if (withComposites) {
                    RekordValue.CompositeList(
                        stored.elements().mapNotNull { parts -> parts?.let { composite(type.elementRekordType(), it) } }
                    )
                } else {
                    null
                }
                else -> stored.toPrimitive(type)?.let { RekordValue.Primitive(it) }
            }
        }
        return values
    }

    /** The composite of [rekordType] filed under [parts], or null when none is filed there. */
    private suspend fun composite(rekordType: String, parts: JsAny): RekordValue.Composite? {
        val id = stringify(rekordKey(rekordType, parts))
        if (id in composites) {
            return composites[id]
        }
        val composite = get(rekordType, parts)?.let { RekordValue.Composite(rekordType, read(it, withComposites = true)) }
        composites[id] = composite
        return composite
    }

    /**
     * The parts of the key [fields] belong under, or null when no ids can be read from them - the
     * type has none declared, or the fields hold none of them.
     */
    private fun keyParts(rekordType: String, fields: Map<String, JsAny?>): JsAny? {
        val idFields = idFields(rekordType)
        if (idFields.isEmpty()) {
            return null
        }
        return jsArrayOf(idFields.map { fieldName ->
            if (fieldName !in fields) return null
            keyComponent(fields[fieldName])
        })
    }

    private fun idFields(rekordType: String): List<String> =
        schema(rekordType).values.filter { it.field.id }.map { it.field.name }

    /**
     * The fields an index is kept for: the searchable ones, and the ids where there are several -
     * a single id being what the key already is.
     */
    private fun indexedFields(rekordType: String): List<String> {
        val fields = schema(rekordType).values
        val idFields = fields.filter { it.field.id }.map { it.field.name }
        val searchableFields = fields.filter { it.field.searchable }.map { it.field.name }
        return (idFields.takeIf { it.size > 1 }.orEmpty() + searchableFields).distinct()
    }

    private fun List<StoredRekord>.sortedByKey(): List<StoredRekord> =
        sortedWith { rekord1, rekord2 -> compareKeys(rekord1.key, rekord2.key) }
}

/**
 * The keys of every rekord of [rekordType], in the rekords store and the search store alike: each
 * begins with the type, and no other type's name begins with it followed by the lowest character.
 */
private fun typeRange(rekordType: String): Key =
    bound(jsArrayOf(listOf(jsString(rekordType))), jsArrayOf(listOf(jsString(rekordType + "\u0000"))), upperOpen = true)

private fun Filter.looksIntoComposites(): Boolean = when (this) {
    is FieldFilter.Nested -> true
    is Filter.And -> filters.any { it.looksIntoComposites() }
    is Filter.Or -> filters.any { it.looksIntoComposites() }
    is Filter.Not -> filter.looksIntoComposites()
    else -> false
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
