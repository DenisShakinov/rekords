@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlin.io.encoding.Base64
import kotlin.reflect.KType

/**
 * The editor a store given a [cipher] runs its operations on: what it hands [editor] has the
 * fields the schema encrypts encrypted - the values written and those a filter compares a field
 * to alike - and what [editor] gives back has them decrypted. [editor] never sees a value of them.
 *
 * An encrypted field is handed over as the [Base64] text of its ciphertext, which is what the
 * storage is told the field holds - see [EncryptedKType].
 */
internal class EncryptingRekordsEditor(
    private val editor: RekordsEditor,
    private val cipher: RekordsCipher,
) : RekordsEditor {

    override val schemaEditor: RekordsSchemaEditor get() = editor.schemaEditor

    /*
     * Each operation is run on [editor] through runOnEditor: an editor's own members are restricted
     * as the code running on it is, and so cannot call another editor's directly.
     */

    override suspend fun put(rekordType: String, values: RekordValues, filter: Filter?) {
        val encryptedValues = values.encrypted(rekordType)
        val encryptedFilter = filter?.encrypted(rekordType)
        runOnEditor(editor) { put(rekordType, encryptedValues, encryptedFilter) }
    }

    override suspend fun delete(rekordType: String, filter: Filter?) {
        val encryptedFilter = filter?.encrypted(rekordType)
        runOnEditor(editor) { delete(rekordType, encryptedFilter) }
    }

    override suspend fun query(
        rekordType: String,
        fields: List<String>?,
        filter: Filter?,
        orderBy: List<Order>?,
        limit: Int?,
        offset: Int?,
    ): List<RekordValues> {
        orderBy?.forEach { order ->
            require(fieldType(rekordType, order.field) !is EncryptedKType) {
                "${order.field} of $rekordType is encrypted, so rekords cannot be ordered by it"
            }
        }
        val encryptedFilter = filter?.encrypted(rekordType)
        return runOnEditor(editor) {
            query(rekordType, fields, encryptedFilter, orderBy, limit, offset)
        }.map { it.decrypted(rekordType) }
    }

    override suspend fun count(rekordType: String, filter: Filter?): Int {
        val encryptedFilter = filter?.encrypted(rekordType)
        return runOnEditor(editor) { count(rekordType, encryptedFilter) }
    }

    /** Runs [action] on the editor of [editor]'s transaction, encrypting what it does too. */
    override suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): T {
        val encryptingAction = EncryptingAction(cipher, action)
        return runOnEditor(editor) { transaction(encryptingAction) }
    }

    override fun close() = editor.close()

    /**
     * The value the rekords already stored are given a [field] a migration adds, as the storage
     * is to be handed it: encrypted, when the field is. A field that cannot be null is given the
     * value of its type that stands for none when [defaultValue] is null, encrypted as any other -
     * the storage, which takes the field for text, would otherwise fill it with no ciphertext.
     *
     * Every rekord is given the same ciphertext, a [Encryption.Randomized] field included: they all
     * hold the same value, which is all the storage can tell from it.
     */
    fun encryptDefaultValue(
        field: FieldWithType,
        defaultValue: PrimitiveRekordValue?,
    ): PrimitiveRekordValue? {
        val type = field.type as? EncryptedKType ?: return defaultValue
        val value = defaultValue.toStoredValue() ?: type.plainType.noneValue() ?: return null
        return encrypt(value, type)
    }

    private fun fieldType(rekordType: String, field: String): KType? =
        schemaEditor.rekordSchema(rekordType)[field]?.type

    private fun RekordValues.encrypted(rekordType: String): RekordValues =
        mapEncrypted(rekordType, ::encrypt)

    private fun RekordValues.decrypted(rekordType: String): RekordValues =
        mapEncrypted(rekordType, ::decrypt)

    /** These values with [transform] applied to each encrypted field, the composites' included. */
    private fun RekordValues.mapEncrypted(
        rekordType: String,
        transform: (PrimitiveRekordValue, EncryptedKType) -> PrimitiveRekordValue,
    ): RekordValues {
        val schema = schemaEditor.rekordSchema(rekordType)
        return mapValues { (fieldName, value) ->
            when (value) {
                null -> null
                is RekordValue.Primitive -> when (val type = schema[fieldName]?.type) {
                    is EncryptedKType -> RekordValue.Primitive(transform(value.value, type))
                    else -> value
                }
                is RekordValue.Composite -> value.mapEncrypted(transform)
                is RekordValue.CompositeList ->
                    RekordValue.CompositeList(value.list.map { it.mapEncrypted(transform) })
            }
        }
    }

    private fun RekordValue.Composite.mapEncrypted(
        transform: (PrimitiveRekordValue, EncryptedKType) -> PrimitiveRekordValue,
    ): RekordValue.Composite = RekordValue.Composite(rekordType, values.mapEncrypted(rekordType, transform))

    /**
     * This filter comparing the encrypted fields to the ciphertexts of what it compares them to,
     * which a [Encryption.Deterministic] field holds for the same values.
     *
     * @throws IllegalArgumentException when it compares an encrypted field in a way no ciphertext
     * tells - by a range, by what it contains - or compares a [Encryption.Randomized] one to
     * anything but null.
     */
    private fun Filter.encrypted(rekordType: String): Filter = when (this) {
        is Filter.And -> Filter.And(filters.map { it.encrypted(rekordType) })
        is Filter.Or -> Filter.Or(filters.map { it.encrypted(rekordType) })
        is Filter.Not -> Filter.Not(filter.encrypted(rekordType))
        is FieldFilter -> encrypted(rekordType)
    }

    private fun FieldFilter.encrypted(rekordType: String): FieldFilter {
        val type: KType? = fieldType(rekordType, field)
        if (this is FieldFilter.Nested) {
            val nestedRekordType = when (type) {
                is RekordKType -> type.rekordType()
                is RekordListKType -> type.elementRekordType()
                else -> return this
            }
            return FieldFilter.Nested(field, filter.encrypted(nestedRekordType))
        }
        if (type !is EncryptedKType) return this
        return when (this) {
            is FieldValueFilter.Equals ->
                FieldValueFilter.Equals(field, value?.let { encryptCompared(rekordType, field, it, type) })
            is FieldFilter.InList ->
                FieldFilter.InList(field, list.map { it?.let { encryptCompared(rekordType, field, it, type) } })
            else -> throw IllegalArgumentException(
                "$field of $rekordType is encrypted, so rekords can be selected by nothing but " +
                    "whether it equals a value"
            )
        }
    }

    private fun encryptCompared(
        rekordType: String,
        field: String,
        value: PrimitiveRekordValue,
        type: EncryptedKType,
    ): PrimitiveRekordValue {
        require(type.encryption == Encryption.Deterministic) {
            "$field of $rekordType is encrypted Randomized, so rekords can be selected by nothing " +
                "but whether it is null. Encrypt it Deterministic to compare it to a value."
        }
        return encrypt(value, type)
    }

    private fun encrypt(value: PrimitiveRekordValue, type: EncryptedKType): PrimitiveRekordValue {
        val deterministic = type.encryption == Encryption.Deterministic
        return Base64.encode(cipher.encrypt(value.toPlaintext(type.plainType), deterministic))
    }

    private fun decrypt(value: PrimitiveRekordValue, type: EncryptedKType): PrimitiveRekordValue {
        check(value is String) {
            "An encrypted field of ${type.plainType} holds $value, which is no ciphertext - was " +
                "it stored before the field was encrypted?"
        }
        return cipher.decrypt(Base64.decode(value)).toPrimitive().coercedTo(type.plainType)
    }
}

/**
 * The value [field] is to be added with, as the storage is to be handed it: encrypted, when this
 * editor encrypts what it hands on. See [EncryptingRekordsEditor.encryptDefaultValue].
 */
internal fun RekordsEditor.storedDefaultValue(
    field: FieldWithType,
    defaultValue: PrimitiveRekordValue?,
): PrimitiveRekordValue? =
    if (this is EncryptingRekordsEditor) encryptDefaultValue(field, defaultValue) else defaultValue

/**
 * The fields of [schema] that are encrypted, as `rekord type.field` - which a store without a
 * cipher cannot keep.
 *
 * @throws IllegalArgumentException when a field is encrypted in a way it cannot be.
 */
internal fun RekordsSchema.encryptedFields(): List<String> =
    rekordTypes.flatMap { rekordClass ->
        rekordClass.allFields()
            .filter { it.type is EncryptedKType }
            .map { "${rekordClass.rekordType()}.${it.field.name}" }
    }

/**
 * Runs [action] on an editor encrypting what it does on the one a transaction hands it - a class
 * rather than a lambda, for the reason the store's actions are.
 */
private class EncryptingAction<T>(
    private val cipher: RekordsCipher,
    private val action: suspend RekordsEditor.() -> T,
) : suspend (RekordsEditor) -> T {

    override suspend fun invoke(editor: RekordsEditor): T =
        runOnEditor(EncryptingRekordsEditor(editor, cipher), action)
}

/*
 * A value is encrypted as a byte telling its kind followed by the value: a whole number as the
 * eight bytes of a Long, a fractional one as the eight of a Double, text as UTF-8. Every whole
 * number is of one kind, as every fractional one is, so that a filter comparing an Int field to a
 * Long encrypts it to the ciphertext the field holds - the way a SQL column matches it unencrypted.
 * Reading it back makes it the type the field is declared with again.
 *
 * The kind is the one of the type the field is declared with, not of the value: Kotlin/JS tells
 * no number from another at runtime, 4.5 being an Int there as much as a Double.
 */

private const val WHOLE: Byte = 1
private const val FRACTIONAL: Byte = 2
private const val TEXT: Byte = 3

/**
 * The plaintext of this value, held by a field of [type].
 *
 * @throws IllegalArgumentException when this is no value a field of [type] holds.
 */
private fun PrimitiveRekordValue.toPlaintext(type: KType): ByteArray {
    val plaintext: ByteArray? = when (type.classifier) {
        Int::class, Long::class, Boolean::class -> when (this) {
            is Boolean -> if (this) 1L else 0L
            is Number -> toLong()
            else -> null
        }?.toPlaintext(WHOLE)
        Float::class, Double::class -> (this as? Number)?.toDouble()?.toRawBits()?.toPlaintext(FRACTIONAL)
        String::class -> (this as? String)?.let { byteArrayOf(TEXT) + it.encodeToByteArray() }
        else -> null
    }
    return requireNotNull(plaintext) { "$this cannot be the value of a field of $type" }
}

private fun Long.toPlaintext(kind: Byte): ByteArray {
    val bytes = ByteArray(1 + Long.SIZE_BYTES)
    bytes[0] = kind
    for (index in 0..<Long.SIZE_BYTES) {
        bytes[1 + index] = (this shr (Long.SIZE_BITS - Byte.SIZE_BITS * (index + 1))).toByte()
    }
    return bytes
}

private fun ByteArray.toPrimitive(): PrimitiveRekordValue = when (first()) {
    WHOLE -> readLong()
    FRACTIONAL -> Double.fromBits(readLong())
    TEXT -> decodeToString(startIndex = 1)
    else -> error("The plaintext of an encrypted field is of no kind known: ${first()}")
}

private fun ByteArray.readLong(): Long {
    check(size == 1 + Long.SIZE_BYTES) { "The plaintext of an encrypted number is $size bytes long" }
    var value = 0L
    for (index in 1..Long.SIZE_BYTES) {
        value = (value shl Byte.SIZE_BITS) or (this[index].toLong() and 0xFF)
    }
    return value
}

private fun PrimitiveRekordValue.coercedTo(type: KType): PrimitiveRekordValue =
    when (type.classifier) {
        Int::class -> (this as? Number)?.toInt() ?: this
        Long::class -> (this as? Number)?.toLong() ?: this
        Float::class -> (this as? Number)?.toFloat() ?: this
        Double::class -> (this as? Number)?.toDouble() ?: this
        Boolean::class -> (this as? Number)?.let { it.toLong() != 0L } ?: this
        else -> this
    }

/** The value of this type that stands for none, or null for a nullable type. */
private fun KType.noneValue(): PrimitiveRekordValue? = when {
    isMarkedNullable -> null
    else -> when (classifier) {
        Int::class -> 0
        Long::class -> 0L
        Float::class -> 0f
        Double::class -> 0.0
        Boolean::class -> false
        String::class -> ""
        else -> throw IllegalArgumentException("No value stands for none of $this")
    }
}
