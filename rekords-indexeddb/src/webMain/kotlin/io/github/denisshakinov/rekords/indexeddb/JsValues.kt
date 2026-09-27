package io.github.denisshakinov.rekords.indexeddb

import kotlin.js.JsAny

/*
 * The few JavaScript operations the editor looks into and builds the values it stores with.
 *
 * Kotlin/JS and Kotlin/Wasm share hardly any API for taking a JavaScript value apart, so each of
 * these is written in JavaScript itself, which both compile the same way. A value handed back to
 * Kotlin as a String, a Double or a Boolean is converted by the call itself.
 */

internal fun jsObject(): JsAny = js("({})")

internal fun jsArray(): JsAny = js("[]")

internal fun jsString(value: String): JsAny = js("value")

internal fun jsNumber(value: Double): JsAny = js("value")

internal fun jsBoolean(value: Boolean): JsAny = js("value")

/** The `typeof` of [value], which is never null here. */
internal fun typeOf(value: JsAny): String = js("typeof value")

internal fun isArray(value: JsAny): Boolean = js("Array.isArray(value)")

internal fun toKotlinString(value: JsAny): String = js("value")

internal fun toKotlinDouble(value: JsAny): Double = js("value")

internal fun toKotlinBoolean(value: JsAny): Boolean = js("value")

internal fun getProperty(target: JsAny, name: String): JsAny? = js("target[name]")

internal fun setProperty(target: JsAny, name: String, value: JsAny?) {
    js("target[name] = value;")
}

private fun propertyNames(target: JsAny): JsAny = js("Object.keys(target)")

private fun arrayLength(array: JsAny): Int = js("array.length")

private fun arrayGet(array: JsAny, index: Int): JsAny? = js("array[index]")

private fun arrayPush(array: JsAny, value: JsAny?) {
    js("array.push(value);")
}

/** [value] as text, the same for any two keys IndexedDB takes to be the same. */
internal fun stringify(value: JsAny?): String = js("JSON.stringify(value)")

/** How IndexedDB orders the keys [first] and [second], the way a walk over a store meets them. */
internal fun compareKeys(first: JsAny, second: JsAny): Int = js("indexedDB.cmp(first, second)")

internal fun JsAny.propertyNames(): List<String> {
    val names = propertyNames(this)
    return List(arrayLength(names)) { index -> toKotlinString(checkNotNull(arrayGet(names, index))) }
}

internal fun JsAny.elements(): List<JsAny?> = List(arrayLength(this)) { index -> arrayGet(this, index) }

internal fun jsArrayOf(elements: List<JsAny?>): JsAny =
    jsArray().also { array -> elements.forEach { arrayPush(array, it) } }
