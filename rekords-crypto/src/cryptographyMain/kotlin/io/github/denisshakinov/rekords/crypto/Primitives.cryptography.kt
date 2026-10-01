package io.github.denisshakinov.rekords.crypto

import dev.whyoleg.cryptography.CryptographyAlgorithm
import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographySystem
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.operations.IvAuthenticatedCipher
import dev.whyoleg.cryptography.operations.IvCipher
import dev.whyoleg.cryptography.random.CryptographyRandom

/*
 * The primitives of the providers cryptography-kotlin's optimal one registers, which are the
 * platform's own. Their blocking calls are what every one of these targets has.
 */

internal actual fun aesGcmKey(key: ByteArray): AesGcmKey {
    val cipher: IvAuthenticatedCipher = algorithm(AES.GCM)
        .keyDecoder()
        .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        .cipher()
    return CryptographyAesGcmKey(cipher)
}

/** Given the IV to encrypt with, which is what the provider takes for delicate. */
@OptIn(DelicateCryptographyApi::class)
private class CryptographyAesGcmKey(private val cipher: IvAuthenticatedCipher) : AesGcmKey {

    override fun encrypt(iv: ByteArray, plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        cipher.encryptWithIvBlocking(iv, plaintext, associatedData)

    override fun decrypt(iv: ByteArray, ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        cipher.decryptWithIvBlocking(iv, ciphertext, associatedData)
}

@OptIn(DelicateCryptographyApi::class)
internal actual fun aesCtrKey(key: ByteArray): AesCtrKey {
    val cipher: IvCipher = algorithm(AES.CTR)
        .keyDecoder()
        .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        .cipher()
    return AesCtrKey { counter, input -> cipher.encryptWithIvBlocking(counter, input) }
}

internal actual fun hmacSha256Key(key: ByteArray): HmacSha256Key {
    val generator = algorithm(HMAC)
        .keyDecoder(SHA256)
        .decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, key)
        .signatureGenerator()
    return HmacSha256Key { data -> generator.generateSignatureBlocking(data) }
}

internal actual fun secureRandomBytes(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

/**
 * [id] of the first provider registered that has it. The default provider has not every one: on
 * Apple platforms it is CryptoKit, which has no AES-CTR, and CommonCrypto, registered after it,
 * stands in for what it lacks.
 */
private fun <A : CryptographyAlgorithm> algorithm(id: CryptographyAlgorithmId<A>): A =
    CryptographySystem.getRegisteredProviders().firstNotNullOfOrNull { it.getOrNull(id) }
        ?: error("No cryptography provider registered has $id")
