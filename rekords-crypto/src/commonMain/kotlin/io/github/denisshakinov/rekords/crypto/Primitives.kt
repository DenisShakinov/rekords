package io.github.denisshakinov.rekords.crypto

/*
 * The primitives the cipher is built of, each target taking them from the implementation it has:
 * cryptography-kotlin's providers where there is one, @noble in the browser. Every one of them is
 * synchronous - see RekordsCipher for why.
 */

/** AES-GCM under one key, with a 128-bit tag. */
internal interface AesGcmKey {

    /** [plaintext] encrypted under [iv], as the ciphertext followed by the tag. */
    fun encrypt(iv: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /**
     * What [encrypt] gave, decrypted.
     *
     * @throws Throwable when the tag does not authenticate [ciphertext] and [associatedData].
     */
    fun decrypt(iv: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * AES-CTR under one key: the keystream from [counter] - the initial counter block, incremented as a
 * 128-bit big-endian number - applied to [input], which encrypts and decrypts alike.
 */
internal fun interface AesCtrKey {
    fun apply(counter: ByteArray, input: ByteArray): ByteArray
}

/** HMAC-SHA256 under one key. */
internal fun interface HmacSha256Key {
    fun sign(data: ByteArray): ByteArray
}

/** AES-GCM under [key], 16, 24 or 32 bytes long. */
internal expect fun aesGcmKey(key: ByteArray): AesGcmKey

/** AES-CTR under [key], 16, 24 or 32 bytes long. */
internal expect fun aesCtrKey(key: ByteArray): AesCtrKey

/** HMAC-SHA256 under [key], which may be of any length but empty. */
internal expect fun hmacSha256Key(key: ByteArray): HmacSha256Key

/** [size] bytes from the platform's cryptographically secure random number generator. */
internal expect fun secureRandomBytes(size: Int): ByteArray

internal const val SHA256_SIZE = 32

/**
 * [length] bytes of key derived from [inputKey] by HKDF with HMAC-SHA256, as RFC 5869 defines it.
 * An empty [salt] stands for as many zero bytes as a hash is long, as it does there.
 */
internal fun hkdfSha256(inputKey: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    require(length in 1..255 * SHA256_SIZE) { "HKDF-SHA256 derives no $length bytes" }
    val pseudorandomKey = hmacSha256Key(salt.takeIf { it.isNotEmpty() } ?: ByteArray(SHA256_SIZE))
        .sign(inputKey)
    val expansion = hmacSha256Key(pseudorandomKey)
    val output = ByteArray(length)
    var block = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < length) {
        block = expansion.sign(block + info + byteArrayOf(counter.toByte()))
        block.copyInto(output, destinationOffset = offset, endIndex = minOf(block.size, length - offset))
        offset += block.size
        counter++
    }
    return output
}
