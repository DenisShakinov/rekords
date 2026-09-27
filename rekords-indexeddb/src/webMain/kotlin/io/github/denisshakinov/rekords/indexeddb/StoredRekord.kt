package io.github.denisshakinov.rekords.indexeddb

import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import kotlin.js.JsAny
import kotlin.reflect.KType

/**
 * A rekord the way the rekords store holds it: under the key `[rekordType, ...parts]`, as the
 * object `{ k: parts, f: fields }`.
 *
 * [parts] are the key the rekord's id fields make, each value as [keyComponent] makes it, or
 * `[[ordinal]]` for a rekord no ids can be read from. They are what a parent holds to refer to the
 * rekord, too: a composite field holds the parts of the rekord it is, a composite list field an
 * array of them - the rekord type being the one the schema declares for the field.
 *
 * [fields] hold each value the way [toStored] makes it, and a field holding none holds `null`: a
 * field is either there or not, which tells a rekord written without it from one written with none.
 */
internal class StoredRekord(
    val rekordType: String,
    val parts: JsAny,
    val fields: MutableMap<String, JsAny?>,
) {

    val key: JsAny get() = rekordKey(rekordType, parts)

    /** Tells this rekord apart from every other one, the way its key does. */
    val id: String get() = stringify(key)

    fun toJs(): JsAny = jsObject().also { rekord ->
        setProperty(rekord, PARTS, parts)
        setProperty(rekord, FIELDS, jsObject().also { values ->
            fields.forEach { (name, value) -> setProperty(values, name, value) }
        })
    }

    companion object {

        private const val PARTS = "k"
        private const val FIELDS = "f"

        fun fromJs(rekordType: String, rekord: JsAny): StoredRekord {
            val values = checkNotNull(getProperty(rekord, FIELDS))
            return StoredRekord(
                rekordType = rekordType,
                parts = checkNotNull(getProperty(rekord, PARTS)),
                fields = values.propertyNames().associateWithTo(mutableMapOf()) { getProperty(values, it) },
            )
        }
    }
}

internal fun rekordKey(rekordType: String, parts: JsAny): JsAny =
    jsArrayOf(listOf(jsString(rekordType)) + parts.elements())

/**
 * The largest integer a JavaScript number holds exactly. A [Long] beyond it is stored as its
 * digits instead, and read back from them.
 */
private const val MAX_SAFE_INTEGER: Long = 9007199254740991L

/** [this] as the value it is stored as. */
internal fun PrimitiveRekordValue?.toStored(): JsAny? = when (this) {
    null -> null
    is String -> jsString(this)
    is Boolean -> jsBoolean(this)
    is Int -> jsNumber(toDouble())
    is Long -> if (this in -MAX_SAFE_INTEGER..MAX_SAFE_INTEGER) jsNumber(toDouble()) else jsString(toString())
    is Float -> jsNumber(toDouble())
    is Double -> jsNumber(this)
    else -> throw IllegalArgumentException("$this cannot be stored in IndexedDB")
}

/**
 * The value [this] stores, as the [type] the schema declares the field with. A field the schema
 * does not know is read as what JavaScript holds: a string, a boolean or a [Double].
 */
internal fun JsAny?.toPrimitive(type: KType?): PrimitiveRekordValue? {
    val stored = this ?: return null
    val classifier = type?.classifier
    return when (typeOf(stored)) {
        "string" -> {
            val text = toKotlinString(stored)
            when (classifier) {
                Int::class -> text.toInt()
                Long::class -> text.toLong()
                else -> text
            }
        }
        "number" -> {
            val number = toKotlinDouble(stored)
            when (classifier) {
                Int::class -> number.toInt()
                Long::class -> number.toLong()
                Float::class -> number.toFloat()
                Boolean::class -> number != 0.0
                else -> number
            }
        }
        "boolean" -> {
            val flag = toKotlinBoolean(stored)
            when (classifier) {
                Int::class -> if (flag) 1 else 0
                Long::class -> if (flag) 1L else 0L
                else -> flag
            }
        }
        else -> throw IllegalStateException("Unexpected ${typeOf(stored)} stored in IndexedDB")
    }
}

/**
 * The [stored] value as it goes into a key. IndexedDB takes neither a boolean nor null as one, so
 * a boolean is its number and null an empty array, which no stored value is.
 */
internal fun keyComponent(stored: JsAny?): JsAny = when {
    stored == null -> jsArray()
    typeOf(stored) == "boolean" -> jsNumber(if (toKotlinBoolean(stored)) 1.0 else 0.0)
    else -> stored
}
