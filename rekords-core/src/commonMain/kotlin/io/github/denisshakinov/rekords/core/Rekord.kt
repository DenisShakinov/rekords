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

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class Field(
    val name: String,
    val id: Boolean = false,
    val searchable: Boolean = false,
)

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