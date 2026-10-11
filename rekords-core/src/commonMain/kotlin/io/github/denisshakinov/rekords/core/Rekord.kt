@file:OptIn(ExperimentalSerializationApi::class)

package io.github.denisshakinov.rekords.core

import io.github.denisshakinov.rekords.core.RekordValue.Primitive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MetaSerializable
import kotlinx.serialization.SerialInfo
import kotlin.jvm.JvmInline
import kotlin.reflect.KType

@MetaSerializable
@Target(AnnotationTarget.CLASS)
annotation class Rekord(val type: String)

/**
 * A group of fields kept within each rekord holding it rather than as a rekord of its own: it has
 * no type of its own, is not declared in a schema, and nothing is stored for it but its fields -
 * as fields of the rekord holding it, named as [Field.prefixed] tells.
 *
 * Its fields are what a storage sees, so they are filtered and ordered by those names. A filter
 * can look into it as into a rekord held, too: [Filter.Nested] on the field holding it selects by
 * its fields. A field holding one that is an id makes each of its fields an id.
 *
 * It may hold values, rekords and other embedded classes, but no list. A field holding one may be
 * none - each of its fields then holding none - unless it is an id; the class has to have a field
 * that cannot be null then, or none would not be told from all of its fields holding none.
 */
@MetaSerializable
@Target(AnnotationTarget.CLASS)
annotation class Embedded

/**
 * @property id whether the field is part of what tells the rekords of a type apart. A field holding
 * a rekord can be one, the rekord then telling them apart by its own ids, as long as it cannot be
 * none; a field holding a list of rekords cannot.
 * @property encryption how the field is encrypted in the storage, which takes a store given a
 * [RekordsCipher]. See [Encryption] for what each way leaves a field to be filtered by.
 * @property prefixed for a field holding an [Embedded] class: whether the fields of the class are
 * named after this one - `game` holding `id` keeping it as `game_id` - or by their own names alone.
 * A field holding anything else is not affected by it.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class Field(
    val name: String,
    val id: Boolean = false,
    val searchable: Boolean = false,
    val encryption: Encryption = Encryption.None,
    val prefixed: Boolean = true,
)

/**
 * How a [Field] is kept in the storage. An encrypted one is stored as text - the ciphertext - so
 * the storage is never handed the value itself, nor told its type.
 *
 * Only a field holding a value is encrypted, not one holding a rekord or a list of them: the
 * fields of the rekords it holds are encrypted each as their own declaration tells.
 */
enum class Encryption {

    /** Stored as it is. */
    None,

    /**
     * Encrypted anew each time, so two rekords holding the same value hold different ciphertexts
     * and the storage cannot tell they are the same. A rekord is selected by nothing but whether
     * the field is null, so the field can be neither an id nor searchable.
     */
    Randomized,

    /**
     * The same value encrypted to the same ciphertext every time, so a rekord is selected by
     * whether the field equals a value - [Filter.Equals], [Filter.InList] - and the field can be
     * an id or searchable. What a range, [Filter.Contains] or an order asks of it stays out of
     * reach.
     *
     * It is what lets the storage tell which rekords hold the same value, and how many do: a field
     * of a few values - a flag, a state - is better encrypted [Randomized].
     */
    Deterministic,
}

typealias FieldWithType = Pair<Field, KType>

val FieldWithType.field: Field get() = first
val FieldWithType.type: KType get() = second

sealed interface RekordValue {
    @JvmInline
    value class Primitive(val value: PrimitiveRekordValue) : RekordValue, Comparable<Primitive> {
        @Suppress("UNCHECKED_CAST")
        override fun compareTo(other: Primitive): Int {
            return (value as Comparable<Any>).compareTo(other.value)
        }
    }

    class Composite(val rekordType: String, val values: RekordValues) : RekordValue
    class CompositeList(val list: List<Composite>) : RekordValue
}

fun Primitive?.compareTo(other: Primitive?): Int {
    return when {
        this == null && other == null -> 0
        other == null -> 1
        this == null -> -1
        else -> compareTo(other)
    }
}

fun PrimitiveRekordValue.asRekordValue(): Primitive = Primitive(this)

typealias PrimitiveRekordValue = Comparable<*>

typealias RekordValues = Map<String, RekordValue?>

typealias PrimitiveRekordValues = Map<String, PrimitiveRekordValue?>

typealias MutableRekordValues = MutableMap<String, RekordValue?>

typealias MutablePrimitiveRekordValues = MutableMap<String, PrimitiveRekordValue?>