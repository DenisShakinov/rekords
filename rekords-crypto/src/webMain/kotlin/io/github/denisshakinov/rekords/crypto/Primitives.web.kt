package io.github.denisshakinov.rekords.crypto

import kotlin.js.JsAny

/*
 * The primitives of @noble/ciphers and @noble/hashes, which are synchronous where WebCrypto is
 * not, and the randomness of crypto.getRandomValues, which WebCrypto has synchronous.
 *
 * Kotlin/JS and Kotlin/Wasm share hardly any API for handing bytes to JavaScript, so the calls are
 * written in JavaScript itself, which both compile the same way.
 */

/** The module `@noble/ciphers/aes.js`, imported the way each target imports one. */
internal expect val nobleAes: JsAny

/** The module `@noble/hashes/hmac.js`. */
internal expect val nobleHmac: JsAny

/** The module `@noble/hashes/sha2.js`. */
internal expect val nobleSha2: JsAny

internal actual fun aesGcmKey(key: ByteArray): AesGcmKey = NobleAesGcmKey(key.toUint8Array())

/** A cipher of @noble is made for one IV and refuses to encrypt twice, so each call makes its own. */
private class NobleAesGcmKey(private val key: JsAny) : AesGcmKey {

    override fun encrypt(iv: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        gcmEncrypt(nobleAes, key, iv.toUint8Array(), plaintext.toUint8Array(), associatedData.toUint8Array())
            .toByteArray()

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        gcmDecrypt(nobleAes, key, iv.toUint8Array(), ciphertext.toUint8Array(), associatedData.toUint8Array())
            .toByteArray()
}

internal actual fun aesCtrKey(key: ByteArray): AesCtrKey {
    val jsKey = key.toUint8Array()
    return AesCtrKey { counter, input -> ctr(nobleAes, jsKey, counter.toUint8Array(), input.toUint8Array()).toByteArray() }
}

internal actual fun hmacSha256Key(key: ByteArray): HmacSha256Key {
    val jsKey = key.toUint8Array()
    return HmacSha256Key { data -> hmacSha256(nobleHmac, nobleSha2, jsKey, data.toUint8Array()).toByteArray() }
}

internal actual fun secureRandomBytes(size: Int): ByteArray = randomUint8Array(size).toByteArray()

private fun gcmEncrypt(aes: JsAny, key: JsAny, iv: JsAny, plaintext: JsAny, associatedData: JsAny): JsAny =
    js("aes.gcm(key, iv, associatedData).encrypt(plaintext)")

private fun gcmDecrypt(aes: JsAny, key: JsAny, iv: JsAny, ciphertext: JsAny, associatedData: JsAny): JsAny =
    js("aes.gcm(key, iv, associatedData).decrypt(ciphertext)")

private fun ctr(aes: JsAny, key: JsAny, counter: JsAny, input: JsAny): JsAny =
    js("aes.ctr(key, counter).encrypt(input)")

private fun hmacSha256(hmac: JsAny, sha2: JsAny, key: JsAny, data: JsAny): JsAny =
    js("hmac.hmac(sha2.sha256, key, data)")

private fun randomUint8Array(size: Int): JsAny = js("globalThis.crypto.getRandomValues(new Uint8Array(size))")

private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

private fun uint8ArrayLength(array: JsAny): Int = js("array.length")

private fun uint8ArrayGet(array: JsAny, index: Int): Int = js("array[index]")

private fun uint8ArraySet(array: JsAny, index: Int, value: Int) {
    js("array[index] = value;")
}

private fun ByteArray.toUint8Array(): JsAny = newUint8Array(size).also { array ->
    forEachIndexed { index, byte -> uint8ArraySet(array, index, byte.toInt() and 0xFF) }
}

private fun JsAny.toByteArray(): ByteArray =
    ByteArray(uint8ArrayLength(this)) { index -> uint8ArrayGet(this, index).toByte() }
