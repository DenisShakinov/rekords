@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.typeOf
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@InternalRekordsApi
fun KSerializer<*>.rekordType(): String = descriptor.rekordType()

private fun SerialDescriptor.rekordType(): String = getRekord().type

@InternalRekordsApi
inline fun <reified T> rekordType(): String = serializer<T>().rekordType()

@OptIn(InternalSerializationApi::class)
@InternalRekordsApi
fun KClass<*>.rekordType(): String = serializer().rekordType()

@InternalRekordsApi
inline fun <reified T> idFields(): List<String> =
    allFields<T>().mapNotNull { if (it.field.id) it.field.name else null }

@InternalRekordsApi
inline fun <reified T> allFields(): List<FieldWithType> =
    serializer<T>().descriptor.allFields()

@InternalRekordsApi
fun SerialDescriptor.allFields(): List<FieldWithType> {
    val fields = mutableListOf<FieldWithType>()
    for (i in 0..<elementsCount) {
        fields.add(getField(i) to getFieldType(i))
    }
    return fields
}

@OptIn(InternalSerializationApi::class)
@InternalRekordsApi
fun KClass<*>.allFields(): List<FieldWithType> =
    serializer().descriptor.allFields()

@InternalRekordsApi
fun RekordKType.rekordType(): String = descriptor.rekordType()

@InternalRekordsApi
fun RekordKType.allFields(): List<FieldWithType> = descriptor.allFields()

@InternalRekordsApi
fun RekordListKType.elementRekordType(): String = elementDescriptor.rekordType()

@InternalRekordsApi
fun RekordListKType.elementAllFields(): List<FieldWithType> = elementDescriptor.allFields()

/**
 * The type the field at [index] is stored as: the one it is declared with, or an [EncryptedKType]
 * for a field the declaration has encrypted.
 *
 * @throws IllegalArgumentException when the field is of a type that cannot be stored, or
 * encrypted in a way it cannot be.
 */
@InternalRekordsApi
fun SerialDescriptor.getFieldType(index: Int): KType {
    val plainType: KType = getPlainFieldType(index)
    val field: Field = getElementAnnotations(index).filterIsInstance<Field>().firstOrNull()
        ?: return plainType
    if (field.encryption == Encryption.None) return plainType
    require(plainType !is RekordKType && plainType !is RekordListKType) {
        "${field.name} of $serialName holds a rekord, which cannot be encrypted as a whole: " +
            "encrypt the fields of the rekord it holds instead."
    }
    require(field.encryption == Encryption.Deterministic || (!field.id && !field.searchable)) {
        "${field.name} of $serialName is encrypted Randomized, which no rekord can be selected " +
            "by, so it can be neither an id nor searchable. Encrypt it Deterministic instead."
    }
    return EncryptedKType(plainType, field.encryption)
}

private fun SerialDescriptor.getPlainFieldType(index: Int): KType {
    val elementDescriptor: SerialDescriptor = getElementDescriptor(index)
    val serialName: String = elementDescriptor.serialName
    val isNullable: Boolean = elementDescriptor.isNullable
    return when {
        // Long, which is what a date is encoded as: the days since the epoch, as toEpochDays() counts them.
        serialName.isClass(LocalDate::class) -> if (isNullable) typeOf<Long?>() else typeOf<Long>()
        serialName.isClass(LocalDateTime::class) -> if (isNullable) typeOf<Long?>() else typeOf<Long>()
        serialName.isClass(Int::class) -> if (isNullable) typeOf<Int?>() else typeOf<Int>()
        serialName.isClass(Long::class) -> if (isNullable) typeOf<Long?>() else typeOf<Long>()
        serialName.isClass(Float::class) -> if (isNullable) typeOf<Float?>() else typeOf<Float>()
        serialName.isClass(Double::class) -> if (isNullable) typeOf<Double?>() else typeOf<Double>()
        serialName.isClass(Boolean::class) -> if (isNullable) typeOf<Boolean?>() else typeOf<Boolean>()
        serialName.isClass(String::class) -> if (isNullable) typeOf<String?>() else typeOf<String>()
        elementDescriptor.annotations.any { it is Rekord } ->
            RekordKType(elementDescriptor, isMarkedNullable = isNullable)
        elementDescriptor.kind == StructureKind.LIST -> {
            val listElementDescriptor = elementDescriptor.getElementDescriptor(0)
            if (!listElementDescriptor.annotations.any { it is Rekord }) {
                throw IllegalArgumentException("Unsupported List element type: ${listElementDescriptor.serialName}")
            }
            RekordListKType(listElementDescriptor, isMarkedNullable = isNullable)
        }
        else -> throw IllegalArgumentException("Unsupported type: $serialName")
    }
}

private interface KAnnotatedElement {
    val annotations: List<Annotation>
}

@InternalRekordsApi
class RekordKType(
    internal val descriptor: SerialDescriptor,
    override val isMarkedNullable: Boolean,
    override val arguments: List<KTypeProjection> = emptyList(),
) : KType, KAnnotatedElement {
    override val classifier: KClassifier? get() = null
    override val annotations: List<Annotation> = emptyList()
}

@InternalRekordsApi
class RekordListKType(
    internal val elementDescriptor: SerialDescriptor,
    override val isMarkedNullable: Boolean,
) : KType, KAnnotatedElement {
    override val classifier: KClassifier get() = List::class
    override val arguments: List<KTypeProjection> =
        listOf(
            KTypeProjection(
                KVariance.OUT,
                RekordKType(elementDescriptor, isMarkedNullable = false)
            )
        )
    override val annotations: List<Annotation> = emptyList()
}

/**
 * The type an encrypted field is stored as: a [String], the ciphertext, nullable as the field is.
 * That is all a storage sees of it, so it keeps the field in a column - or whatever stands for one
 * - of text.
 *
 * @property plainType the type the field holds before it is encrypted, the value of which is what
 * reading the field gives back.
 * @property encryption how the field is encrypted, never [Encryption.None].
 */
@InternalRekordsApi
class EncryptedKType(
    val plainType: KType,
    val encryption: Encryption,
) : KType, KAnnotatedElement {
    override val classifier: KClassifier get() = String::class
    override val arguments: List<KTypeProjection> get() = emptyList()
    override val isMarkedNullable: Boolean get() = plainType.isMarkedNullable
    override val annotations: List<Annotation> = emptyList()

    override fun toString(): String = "String (${encryption.name} encrypted $plainType)"
}

/**
 * Whether this serial name is [clazz], nullable or not: a nullable element's serial name carries
 * a trailing "?", and missing it means a value is stored in one shape and looked up in another.
 */
private fun String.isClass(clazz: KClass<*>): Boolean {
    return endsWith(clazz.simpleName.orEmpty()) || endsWith("${clazz.simpleName}?")
}

@OptIn(ExperimentalSerializationApi::class)
internal class RekordValuesEncoder(
    private val onComplete: ((MutableRekordValues) -> Unit)? = null,
) : AbstractEncoder() {
    internal val values: MutableRekordValues = mutableMapOf()

    private var elementIndex = 0
    private var elementName = ""
    private lateinit var descriptor: SerialDescriptor
    private var isCompositeField = false

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        this.elementIndex = index
        this.descriptor = descriptor
        val fieldAnnotation =
            descriptor.getElementAnnotations(index).filterIsInstance<Field>().firstOrNull()
                ?: return false
        elementName = fieldAnnotation.name
        val kind = descriptor.getElementDescriptor(index).kind
        isCompositeField = kind == StructureKind.CLASS || kind == StructureKind.LIST
        if (!isCompositeField) values[elementName] = null
        return true
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (!isCompositeField) return this
        isCompositeField = false
        val name = elementName
        return when (descriptor.kind) {
            StructureKind.LIST -> {
                val rekordType = descriptor.getElementDescriptor(0).getRekord().type
                RekordValuesCollectionEncoder(rekordType) { compositeList ->
                    values[name] = compositeList
                }
            }
            else -> {
                val rekordType = descriptor.getRekord().type
                RekordValuesEncoder { nestedValues ->
                    values[name] = RekordValue.Composite(rekordType, nestedValues)
                }
            }
        }
    }

    override fun encodeNull() {
        // null already set in encodeElement
    }

    override fun encodeValue(value: Any) {
        values[elementName] = RekordValue.Primitive(value as PrimitiveRekordValue)
    }

    override fun encodeString(value: String) {
        val elementDescriptor: SerialDescriptor = descriptor.getElementDescriptor(elementIndex)
        val serialName: String = elementDescriptor.serialName
        val encoded: PrimitiveRekordValue = when {
            serialName.isClass(LocalDate::class) -> LocalDate.parse(value).encode()!!
            serialName.isClass(LocalDateTime::class) -> LocalDateTime.parse(value).encode()!!
            else -> value
        }
        values[elementName] = encoded.asRekordValue()
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        onComplete?.invoke(values)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class RekordValuesCollectionEncoder(
    private val rekordType: String,
    private val onComplete: (RekordValue.CompositeList) -> Unit,
) : AbstractEncoder() {
    private val items = mutableListOf<RekordValue.Composite>()

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean = true

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
        RekordValuesEncoder { nestedValues ->
            items.add(RekordValue.Composite(rekordType, nestedValues))
        }

    override fun endStructure(descriptor: SerialDescriptor) {
        onComplete(RekordValue.CompositeList(items))
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal class RekordValuesDecoder(private val values: RekordValues) : AbstractDecoder() {
    private var elementIndex = 0
    private var elementName = ""

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeValue(): Any {
        val v = values[elementName]
        return when (v) {
            is RekordValue.Primitive -> v.value
            else -> error("Expected Primitive for field '$elementName', got $v")
        }
    }

    override fun decodeNotNullMark(): Boolean = values[elementName] != null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (elementIndex < descriptor.elementsCount) {
            val index = elementIndex++
            val fieldAnnotation = descriptor.getElementAnnotations(index)
                .filterIsInstance<Field>().firstOrNull() ?: continue
            elementName = fieldAnnotation.name
            return index
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (val v = values[elementName]) {
            is RekordValue.Composite -> RekordValuesDecoder(v.values)
            is RekordValue.CompositeList -> RekordValuesCollectionDecoder(v.list)
            else -> this
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class RekordValuesCollectionDecoder(
    private val items: List<RekordValue.Composite>,
) : AbstractDecoder() {
    private var decodedIndex = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = items.size

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (decodedIndex < items.size) decodedIndex else CompositeDecoder.DECODE_DONE

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        RekordValuesDecoder(items[decodedIndex++].values)
}

@InternalRekordsApi
fun <T> encode(rekord: T, serializer: SerializationStrategy<T>): RekordValues {
    val encoder = RekordValuesEncoder()
    encoder.encodeSerializableValue(serializer, rekord)
    return encoder.values
}

@InternalRekordsApi
inline fun <reified T> encode(rekord: T): RekordValues = encode(rekord, serializer())

@InternalRekordsApi
fun <T> RekordValues.decode(deserializer: DeserializationStrategy<T>): T {
    val descriptor: SerialDescriptor = deserializer.descriptor
    val mutableValues: MutableRekordValues = this.toMutableMap()
    for (elementIndex in 0..<descriptor.elementsCount) {
        val fieldAnnotation = descriptor.getElementAnnotations(elementIndex)
            .filterIsInstance<Field>().firstOrNull() ?: continue
        val elementName = fieldAnnotation.name
        val value: RekordValue? = this[elementName]
        if (value !is RekordValue.Primitive) continue
        val primitive = value.value
        if (primitive !is Number) continue
        val elementDescriptor: SerialDescriptor = descriptor.getElementDescriptor(elementIndex)
        val serialName: String = elementDescriptor.serialName
        val coerced: PrimitiveRekordValue = when {
            serialName.isClass(LocalDate::class) -> primitive.toLong().toLocalDate().toString()
            serialName.isClass(LocalDateTime::class) ->
                primitive.toLong().toLocalDateTime().toString()
            serialName.isClass(Int::class) -> primitive.toInt()
            serialName.isClass(Float::class) -> primitive.toFloat()
            serialName.isClass(Boolean::class) -> primitive == 1 || primitive == 1L
            else -> continue
        }
        mutableValues[elementName] = RekordValue.Primitive(coerced)
    }
    return RekordValuesDecoder(mutableValues).decodeSerializableValue(deserializer)
}

@InternalRekordsApi
inline fun <reified T> RekordValues.decode(): T = decode(serializer())

@InternalRekordsApi
fun SerialDescriptor.getRekord(): Rekord =
    annotations.first { it is Rekord } as Rekord

@InternalRekordsApi
fun SerialDescriptor.getField(index: Int): Field =
    getElementAnnotations(index).first { it is Field } as Field

private fun LocalDate.toLong(): Long = toEpochDays()

private fun Long.toLocalDate(): LocalDate = LocalDate.fromEpochDays(this)

@OptIn(ExperimentalTime::class)
private fun LocalDateTime.toLong(): Long = toInstant(TimeZone.UTC).toEpochMilliseconds()

@OptIn(ExperimentalTime::class)
private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC)

/**
 * The value as a storage keeps it, the way the fields of a rekord written are encoded: a date as
 * the number it is stored as, anything else as it is.
 */
@InternalRekordsApi
fun PrimitiveRekordValue?.toStoredValue(): PrimitiveRekordValue? = encode()

internal fun PrimitiveRekordValue?.encode(): PrimitiveRekordValue? {
    return when (this) {
        is LocalDate -> toLong()
        is LocalDateTime -> toLong()
        else -> this
    }
}