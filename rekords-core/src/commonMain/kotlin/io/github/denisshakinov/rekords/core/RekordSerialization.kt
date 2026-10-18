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

/**
 * The ids of a rekord with these fields, each as the fields its value is reached through: an id
 * of its own is the field alone, one holding a rekord is that field followed by each id of the
 * rekord held, as deep as they nest.
 */
@InternalRekordsApi
fun Collection<FieldWithType>.idPaths(): List<List<String>> =
    filter { it.field.id }.flatMap { (field, type) ->
        if (type is RekordKType) {
            type.allFields().idPaths().map { path -> listOf(field.name) + path }
        } else {
            listOf(listOf(field.name))
        }
    }

/** Whether these values reach as far as the field at the end of [path]. */
@InternalRekordsApi
fun RekordValues.containsPath(path: List<String>): Boolean {
    val composite: RekordValues = path.dropLast(1).fold(this) { values, fieldName ->
        (values[fieldName] as? RekordValue.Composite)?.values ?: return false
    }
    return composite.containsKey(path.last())
}

/** The value of the field at the end of [path], or null when it holds none or is not reached. */
@InternalRekordsApi
fun RekordValues.primitiveAt(path: List<String>): PrimitiveRekordValue? {
    val composite: RekordValues = path.dropLast(1).fold(this) { values, fieldName ->
        (values[fieldName] as? RekordValue.Composite)?.values ?: return null
    }
    return (composite[path.last()] as? RekordValue.Primitive)?.value
}

@InternalRekordsApi
inline fun <reified T> allFields(): List<FieldWithType> =
    serializer<T>().descriptor.allFields()

/**
 * The fields a rekord of this descriptor is stored as. A field holding an [Embedded] class is not
 * one of them: the fields of the class are, in its place - see [embeddedFields].
 */
@InternalRekordsApi
fun SerialDescriptor.allFields(): List<FieldWithType> {
    val fields = mutableListOf<FieldWithType>()
    for (i in 0..<elementsCount) {
        if (getElementDescriptor(i).isEmbedded()) {
            fields.addAll(embeddedFields(i))
        } else {
            fields.add(getField(i) to getFieldType(i))
        }
    }
    return fields
}

/** Whether this is the descriptor of an [Embedded] class, nullable or not. */
internal fun SerialDescriptor.isEmbedded(): Boolean = annotations.any { it is Embedded }

/** What the names of the fields of the embedded class this field holds begin with. */
internal fun Field.embeddedPrefix(): String = if (prefixed) "${name}_" else ""

/**
 * The fields the embedded class held by the field at [index] is stored as, as fields of this
 * rekord: named as [Field.prefixed] tells, ids when the field holding them is one, and nullable
 * when it is.
 *
 * @throws IllegalArgumentException when the class cannot be embedded the way the field holds it.
 */
private fun SerialDescriptor.embeddedFields(index: Int): List<FieldWithType> {
    val field: Field = getField(index)
    val embedded: SerialDescriptor = getElementDescriptor(index)
    val isNullable: Boolean = embedded.isNullable
    require(field.encryption == Encryption.None) {
        "${field.name} of $serialName holds an embedded class, which cannot be encrypted as a " +
            "whole: encrypt the fields of the class instead."
    }
    require(!field.id || !isNullable) {
        "${field.name} of $serialName holds an embedded class that may be none, which cannot be " +
            "an id: make it non-null."
    }
    val fields: List<FieldWithType> = embedded.allFields()
    require(fields.none { it.type is RekordListKType }) {
        "${field.name} of $serialName holds an embedded class holding a list, which it cannot: " +
            "the list would need a table of its own."
    }
    require(!isNullable || fields.any { !it.type.isMarkedNullable }) {
        "${field.name} of $serialName holds an embedded class that may be none, none of the " +
            "fields of which cannot be null: none of it would not be told from all of them " +
            "holding none. Give the class a field that cannot be null, or make the field non-null."
    }
    val prefix: String = field.embeddedPrefix()
    return fields.map { (innerField, innerType) ->
        val embeddedField = Field(
            name = prefix + innerField.name,
            id = field.id,
            searchable = field.searchable || innerField.searchable,
            encryption = innerField.encryption,
        )
        require(!embeddedField.id || innerType !is RekordKType || !innerType.isMarkedNullable) {
            "${innerField.name} of ${embedded.serialName} holds a rekord that may be none, which " +
                "cannot be an id, as ${field.name} of $serialName makes it: make it non-null."
        }
        require(innerField.encryption != Encryption.Randomized || (!embeddedField.id && !embeddedField.searchable)) {
            "${innerField.name} of ${embedded.serialName} is encrypted Randomized, which no rekord " +
                "can be selected by, so it can be neither an id nor searchable, as ${field.name} " +
                "of $serialName makes it. Encrypt it Deterministic instead."
        }
        embeddedField to if (isNullable) innerType.nullable() else innerType
    }
}

/** This type, holding none as well. */
private fun KType.nullable(): KType = when {
    isMarkedNullable -> this
    this is RekordKType -> RekordKType(descriptor, isMarkedNullable = true)
    this is RekordListKType -> RekordListKType(elementDescriptor, isMarkedNullable = true)
    this is EncryptedKType -> EncryptedKType(plainType.nullable(), encryption)
    else -> when (classifier) {
        Int::class -> typeOf<Int?>()
        Long::class -> typeOf<Long?>()
        Float::class -> typeOf<Float?>()
        Double::class -> typeOf<Double?>()
        Boolean::class -> typeOf<Boolean?>()
        String::class -> typeOf<String?>()
        else -> throw IllegalArgumentException("Unsupported type: $this")
    }
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
 * @throws IllegalArgumentException when the field is of a type that cannot be stored, is an id
 * holding a list or a rekord that may be none, or is encrypted in a way it cannot be.
 */
@InternalRekordsApi
fun SerialDescriptor.getFieldType(index: Int): KType {
    val plainType: KType = getPlainFieldType(index)
    val field: Field = getElementAnnotations(index).filterIsInstance<Field>().firstOrNull()
        ?: return plainType
    // A rekord held as an id is told apart by its own ids, so there has to be one, and only one.
    require(!field.id || plainType !is RekordListKType) {
        "${field.name} of $serialName holds a list of rekords, which cannot be an id."
    }
    require(!field.id || plainType !is RekordKType || !plainType.isMarkedNullable) {
        "${field.name} of $serialName holds a rekord that may be none, which cannot be an id: " +
            "make it non-null."
    }
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

/**
 * Encodes a rekord into [values], each field under [prefix] and its name: an embedded class is
 * encoded into the very values of the rekord holding it, under the prefix of the field holding it.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class RekordValuesEncoder(
    internal val values: MutableRekordValues = mutableMapOf(),
    private val prefix: String = "",
    private val onComplete: ((MutableRekordValues) -> Unit)? = null,
) : AbstractEncoder() {

    private var elementIndex = 0
    private var elementName = ""
    private lateinit var descriptor: SerialDescriptor
    private var isCompositeField = false

    /** The field holding the embedded class being encoded, if one is. */
    private var embeddedField: Field? = null

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        this.elementIndex = index
        this.descriptor = descriptor
        val fieldAnnotation =
            descriptor.getElementAnnotations(index).filterIsInstance<Field>().firstOrNull()
                ?: return false
        elementName = prefix + fieldAnnotation.name
        val elementDescriptor = descriptor.getElementDescriptor(index)
        if (elementDescriptor.isEmbedded()) {
            // Each of its fields holds none until it is written, which one that is none never is.
            descriptor.embeddedFields(index).forEach { (field, _) -> values[prefix + field.name] = null }
            embeddedField = fieldAnnotation
            isCompositeField = false
            return true
        }
        embeddedField = null
        val kind = elementDescriptor.kind
        isCompositeField = kind == StructureKind.CLASS || kind == StructureKind.LIST
        if (!isCompositeField) values[elementName] = null
        return true
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        embeddedField?.let { field ->
            embeddedField = null
            return RekordValuesEncoder(values = values, prefix = prefix + field.embeddedPrefix())
        }
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

    /**
     * A field that holds nothing is written as null, a rekord or a list of them included - one
     * left out of the values would be taken for one not written, and keep what it held before.
     */
    override fun encodeNull() {
        isCompositeField = false
        // An embedded class that is none leaves each of its fields holding none, as they are.
        if (embeddedField != null) {
            embeddedField = null
            return
        }
        values[elementName] = null
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

/**
 * Decodes a rekord from [values], each field read under [prefix] and its name: an embedded class
 * is decoded from the very values of the rekord holding it, under the prefix of the field holding it.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class RekordValuesDecoder(
    private val values: RekordValues,
    private val prefix: String = "",
) : AbstractDecoder() {
    private var elementIndex = 0
    private var elementName = ""
    private var descriptor: SerialDescriptor? = null
    private var fieldIndex = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeValue(): Any {
        val v = values[elementName]
        return when (v) {
            is RekordValue.Primitive -> v.value
            else -> error("Expected Primitive for field '$elementName', got $v")
        }
    }

    override fun decodeNotNullMark(): Boolean {
        val descriptor: SerialDescriptor = descriptor ?: return values[elementName] != null
        if (!descriptor.getElementDescriptor(fieldIndex).isEmbedded()) return values[elementName] != null
        // An embedded class is none when each of its fields holds none.
        return descriptor.embeddedFields(fieldIndex).any { (field, _) -> values[prefix + field.name] != null }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (elementIndex < descriptor.elementsCount) {
            val index = elementIndex++
            val fieldAnnotation = descriptor.getElementAnnotations(index)
                .filterIsInstance<Field>().firstOrNull() ?: continue
            elementName = prefix + fieldAnnotation.name
            this.descriptor = descriptor
            fieldIndex = index
            return index
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (descriptor.isEmbedded()) {
            val field: Field = checkNotNull(this.descriptor).getField(fieldIndex)
            return RekordValuesDecoder(values, prefix + field.embeddedPrefix())
        }
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
    val mutableValues: MutableRekordValues = this.toMutableMap()
    mutableValues.coerce(deserializer.descriptor, prefix = "")
    return RekordValuesDecoder(mutableValues).decodeSerializableValue(deserializer)
}

/**
 * Turns each number these values hold for a field of [descriptor] - named under [prefix] - into
 * the type the field is declared with: the fields of an embedded class included, and those of the
 * rekords held, as deep as they nest, which an engine reads back as stored as well.
 */
private fun MutableRekordValues.coerce(descriptor: SerialDescriptor, prefix: String) {
    for (elementIndex in 0..<descriptor.elementsCount) {
        val fieldAnnotation = descriptor.getElementAnnotations(elementIndex)
            .filterIsInstance<Field>().firstOrNull() ?: continue
        val elementDescriptor: SerialDescriptor = descriptor.getElementDescriptor(elementIndex)
        if (elementDescriptor.isEmbedded()) {
            coerce(elementDescriptor, prefix + fieldAnnotation.embeddedPrefix())
            continue
        }
        val elementName = prefix + fieldAnnotation.name
        when (val value: RekordValue? = this[elementName]) {
            is RekordValue.Composite -> this[elementName] = value.coerced(elementDescriptor)
            is RekordValue.CompositeList -> this[elementName] = RekordValue.CompositeList(
                value.list.map { it.coerced(elementDescriptor.getElementDescriptor(0)) },
            )
            is RekordValue.Primitive -> {
                val coerced: PrimitiveRekordValue =
                    value.value.coercedTo(elementDescriptor.serialName) ?: continue
                this[elementName] = RekordValue.Primitive(coerced)
            }
            null -> continue
        }
    }
}

/** This rekord held, with its numbers turned into the types of the fields of [descriptor]. */
private fun RekordValue.Composite.coerced(descriptor: SerialDescriptor): RekordValue.Composite =
    RekordValue.Composite(rekordType, values.toMutableMap().apply { coerce(descriptor, prefix = "") })

/** This value turned into the type of [serialName], or null for one that needs no turning. */
private fun PrimitiveRekordValue.coercedTo(serialName: String): PrimitiveRekordValue? {
    if (this !is Number) return null
    return when {
        serialName.isClass(LocalDate::class) -> toLong().toLocalDate().toString()
        serialName.isClass(LocalDateTime::class) -> toLong().toLocalDateTime().toString()
        serialName.isClass(Int::class) -> toInt()
        serialName.isClass(Float::class) -> toFloat()
        serialName.isClass(Boolean::class) -> this == 1 || this == 1L
        else -> null
    }
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