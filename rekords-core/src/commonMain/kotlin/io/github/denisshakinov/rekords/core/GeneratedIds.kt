@file:OptIn(InternalRekordsApi::class, ExperimentalUuidApi::class)

package io.github.denisshakinov.rekords.core

import kotlin.reflect.KType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The largest number known to be taken by a [Field.generated] id of each rekord type, kept while
 * the rekords of one call are given theirs - one [putRekord], or all of [putRekords] - so that the
 * storage is asked for its largest once for each type in the call, however many rekords it puts,
 * and told the largest given once, by [keepGeneratedIds].
 *
 * It is told of the numbers the rekords put already hold before any of theirs is given, so that no
 * rekord is given one that another put along with it holds. What else is written meanwhile it does
 * not see, so it is not to outlive the call.
 */
@InternalRekordsApi
class GeneratedIdCounters {

    internal val largest: MutableMap<String, Long> = mutableMapOf()

    /** The types the largest number taken has been read of from the storage, and is in [largest]. */
    internal val read: MutableSet<String> = mutableSetOf()

    /** The types a number has been given to, which the storage is to keep the largest of. */
    internal val generated: MutableSet<String> = mutableSetOf()
}

/**
 * Has the storage keep the largest number [counters] gave each type, for none of them to be given
 * again - see [RekordsSchemaEditor.largestGeneratedId].
 */
@InternalRekordsApi
suspend fun RekordsEditor.keepGeneratedIds(counters: GeneratedIdCounters) {
    for (rekordType in counters.generated) {
        updateLargestGeneratedId(rekordType, counters.largest.getValue(rekordType))
    }
}

/**
 * [values] of a rekord of [rekordType] with these [fields], each [Field.generated] id holding none
 * yet given one - the rekord's own and those of the rekords it holds, as deep as they nest - or
 * [values] themselves, the very same map, when none was.
 *
 * A number is the largest one taken, plus one: ever given - see
 * [RekordsSchemaEditor.largestGeneratedId] - or held by a rekord stored, both read once for each
 * type from the editor this runs on, what the transaction has written included; or given, by
 * [counters] to another rekord or by a rekord put along with this one to itself. The storage is
 * not told of the numbers given until [keepGeneratedIds].
 */
@InternalRekordsApi
suspend fun RekordsEditor.generateIds(
    rekordType: String,
    values: RekordValues,
    fields: List<FieldWithType>,
    counters: GeneratedIdCounters = GeneratedIdCounters(),
): RekordValues {
    counters.noteGivenIds(rekordType, values, fields)
    return generateIdsNoted(rekordType, values, fields, counters)
}

/**
 * Tells [GeneratedIdCounters] of each number the generated ids of [values] - and of the rekords
 * they hold, as deep as they nest - already hold.
 */
private fun GeneratedIdCounters.noteGivenIds(
    rekordType: String,
    values: RekordValues,
    fields: List<FieldWithType>,
) {
    for ((field, type) in fields) {
        val value: RekordValue? = values[field.name]
        when {
            type is RekordKType && value is RekordValue.Composite ->
                noteGivenIds(value.rekordType, value.values, type.allFields())
            type is RekordListKType && value is RekordValue.CompositeList -> {
                val fieldsOfHeld = type.elementAllFields()
                value.list.forEach { noteGivenIds(it.rekordType, it.values, fieldsOfHeld) }
            }
            field.generated -> {
                val number: Long = ((value as? RekordValue.Primitive)?.value as? Number)?.toLong() ?: continue
                largest[rekordType] = maxOf(largest[rekordType] ?: 0L, number)
            }
        }
    }
}

/** See [generateIds], the numbers [values] already hold having been noted in [counters]. */
private suspend fun RekordsEditor.generateIdsNoted(
    rekordType: String,
    values: RekordValues,
    fields: List<FieldWithType>,
    counters: GeneratedIdCounters,
): RekordValues {
    var generated: MutableRekordValues? = null
    fun set(fieldName: String, value: RekordValue) {
        (generated ?: values.toMutableMap().also { generated = it })[fieldName] = value
    }
    for ((field, type) in fields) {
        val value: RekordValue? = values[field.name]
        when {
            type is RekordKType && value is RekordValue.Composite -> {
                val held = generateIdsNoted(value, type.allFields(), counters)
                if (held !== value) set(field.name, held)
            }
            type is RekordListKType && value is RekordValue.CompositeList -> {
                val fieldsOfHeld = type.elementAllFields()
                val held = value.list.map { generateIdsNoted(it, fieldsOfHeld, counters) }
                if (held.indices.any { held[it] !== value.list[it] }) {
                    set(field.name, RekordValue.CompositeList(held))
                }
            }
            field.generated && value.holdsNoGeneratedId() ->
                set(field.name, generateId(rekordType, field.name, type, counters))
        }
    }
    return generated ?: values
}

private suspend fun RekordsEditor.generateIdsNoted(
    composite: RekordValue.Composite,
    fields: List<FieldWithType>,
    counters: GeneratedIdCounters,
): RekordValue.Composite {
    val values = generateIdsNoted(composite.rekordType, composite.values, fields, counters)
    return if (values === composite.values) composite else RekordValue.Composite(composite.rekordType, values)
}

/** Whether this is what a generated id holds before it is given one: `0` or `""`. */
private fun RekordValue?.holdsNoGeneratedId(): Boolean =
    when (val value = (this as? RekordValue.Primitive)?.value) {
        is Int -> value == 0
        is Long -> value == 0L
        is String -> value.isEmpty()
        else -> false
    }

/** An id for the field [fieldName] of [type] of a rekord of [rekordType]. */
private suspend fun RekordsEditor.generateId(
    rekordType: String,
    fieldName: String,
    type: KType,
    counters: GeneratedIdCounters,
): RekordValue.Primitive {
    val plainType: KType = (type as? EncryptedKType)?.plainType ?: type
    if (plainType.classifier == String::class) return Uuid.random().toString().asRekordValue()
    if (counters.read.add(rekordType)) {
        // Both: a number given and deleted since is held by no rekord, and a rekord may hold one
        // never given - put with it, or by an editor keeping none.
        val largestGiven: Long = largestGeneratedId(rekordType)
        val largestStored: Long = query(
            rekordType = rekordType,
            fields = listOf(fieldName),
            orderBy = listOf(Order.Descending(fieldName)),
            limit = 1,
        ).firstOrNull()?.let { (it[fieldName] as? RekordValue.Primitive)?.value as? Number }?.toLong() ?: 0L
        counters.largest[rekordType] = maxOf(counters.largest[rekordType] ?: 0L, largestGiven, largestStored)
    }
    val next: Long = (counters.largest[rekordType] ?: 0L) + 1
    counters.largest[rekordType] = next
    counters.generated += rekordType
    if (plainType.classifier == Int::class) {
        check(next <= Int.MAX_VALUE) {
            "$fieldName of $rekordType has run out of Int ids: the next would be $next. Make it a Long."
        }
        return next.toInt().asRekordValue()
    }
    return next.asRekordValue()
}
