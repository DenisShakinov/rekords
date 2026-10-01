package io.github.denisshakinov.rekords.crypto

import io.github.denisshakinov.rekords.core.Encryption
import io.github.denisshakinov.rekords.core.RekordsCipher

/**
 * Encrypts the fields of rekords with AES-256, under keys it derives from [key] by HKDF-SHA256:
 * ```kotlin
 * val store = RekordsStore(AppRekordsSchema(), cipher = AesRekordsCipher(key)) { name = "app" }
 * ```
 *
 * A field encrypted [Encryption.Randomized] is encrypted with AES-GCM under a random 96-bit IV:
 * `1 | IV (12) | ciphertext | tag (16)`.
 *
 * One encrypted [Encryption.Deterministic] is encrypted with SIV, as RFC 5297 builds it, HMAC-SHA256
 * standing for its S2V: the synthetic IV is the HMAC of the plaintext cut to 128 bits, AES-CTR
 * encrypts the plaintext from it, and decrypting checks it is the one the plaintext gives - which is
 * what authenticates the ciphertext. The same plaintext is the same ciphertext, and nothing else is
 * learnt of it: `2 | synthetic IV (16) | ciphertext`. GCM is left out of it on purpose: its security
 * stands on an IV being used once, which SIV needs not, and JCA refuses an IV used just before.
 *
 * The leading byte, telling which of the two a ciphertext is, is authenticated by both. Every
 * target gives the same bytes for the same key and plaintext, deterministically encrypted.
 *
 * The primitives are the platform's own, through cryptography-kotlin - JCA on the JVM and Android,
 * CryptoKit and CommonCrypto on Apple platforms, OpenSSL on Linux and Windows - and @noble/ciphers
 * and @noble/hashes in the browser: WebCrypto is asynchronous only, which a cipher cannot be, as
 * [RekordsCipher] tells.
 *
 * Keeping [key] is the application's business - in the Android Keystore, the iOS Keychain - and
 * what it encrypted cannot be read without it.
 *
 * @param key the 32 bytes of the key, such as [generateKey] gives.
 */
class AesRekordsCipher(key: ByteArray) : RekordsCipher {

    init {
        require(key.size == KEY_SIZE) { "The key of AesRekordsCipher is $KEY_SIZE bytes, not ${key.size}" }
    }

    private val randomizedKey: AesGcmKey = aesGcmKey(derive(key, RANDOMIZED_KEY_INFO))
    private val deterministicKey: AesCtrKey = aesCtrKey(derive(key, DETERMINISTIC_KEY_INFO))
    private val syntheticIvKey: HmacSha256Key = hmacSha256Key(derive(key, SYNTHETIC_IV_KEY_INFO))

    override fun encrypt(plaintext: ByteArray, deterministic: Boolean): ByteArray =
        if (deterministic) encryptDeterministic(plaintext) else encryptRandomized(plaintext)

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.isNotEmpty()) { "An empty ciphertext is none" }
        return when (ciphertext[0]) {
            RANDOMIZED -> decryptRandomized(ciphertext)
            DETERMINISTIC -> decryptDeterministic(ciphertext)
            else -> throw IllegalArgumentException("The ciphertext is of no mode known: ${ciphertext[0]}")
        }
    }

    private fun encryptRandomized(plaintext: ByteArray): ByteArray {
        val iv = secureRandomBytes(GCM_IV_SIZE)
        return byteArrayOf(RANDOMIZED) + iv + randomizedKey.encrypt(iv, plaintext, RANDOMIZED_ASSOCIATED_DATA)
    }

    private fun decryptRandomized(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= 1 + GCM_IV_SIZE + GCM_TAG_SIZE) { "The ciphertext is too short to be one" }
        return randomizedKey.decrypt(
            iv = ciphertext.copyOfRange(1, 1 + GCM_IV_SIZE),
            ciphertext = ciphertext.copyOfRange(1 + GCM_IV_SIZE, ciphertext.size),
            associatedData = RANDOMIZED_ASSOCIATED_DATA,
        )
    }

    private fun encryptDeterministic(plaintext: ByteArray): ByteArray {
        val syntheticIv = syntheticIv(plaintext)
        return byteArrayOf(DETERMINISTIC) + syntheticIv + deterministicKey.apply(syntheticIv.toCounter(), plaintext)
    }

    private fun decryptDeterministic(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= 1 + SYNTHETIC_IV_SIZE) { "The ciphertext is too short to be one" }
        val syntheticIv = ciphertext.copyOfRange(1, 1 + SYNTHETIC_IV_SIZE)
        val plaintext = deterministicKey.apply(
            counter = syntheticIv.toCounter(),
            input = ciphertext.copyOfRange(1 + SYNTHETIC_IV_SIZE, ciphertext.size),
        )
        check(syntheticIv(plaintext).contentEqualsInConstantTime(syntheticIv)) {
            "The ciphertext does not authenticate: it was tampered with, or another key encrypted it"
        }
        return plaintext
    }

    /** The HMAC of the mode and [plaintext], cut to the 128 bits of a counter block. */
    private fun syntheticIv(plaintext: ByteArray): ByteArray =
        syntheticIvKey.sign(byteArrayOf(DETERMINISTIC) + plaintext).copyOf(SYNTHETIC_IV_SIZE)

    companion object {

        /** How many bytes a key is. */
        const val KEY_SIZE: Int = 32

        /** A new key, from the platform's cryptographically secure random number generator. */
        fun generateKey(): ByteArray = secureRandomBytes(KEY_SIZE)

        private const val RANDOMIZED: Byte = 1
        private const val DETERMINISTIC: Byte = 2

        private val RANDOMIZED_ASSOCIATED_DATA = byteArrayOf(RANDOMIZED)

        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_SIZE = 16
        private const val SYNTHETIC_IV_SIZE = 16

        private const val RANDOMIZED_KEY_INFO = "rekords-crypto randomized aes-256-gcm"
        private const val DETERMINISTIC_KEY_INFO = "rekords-crypto deterministic aes-256-ctr"
        private const val SYNTHETIC_IV_KEY_INFO = "rekords-crypto deterministic hmac-sha256"

        private fun derive(key: ByteArray, info: String): ByteArray =
            hkdfSha256(key, salt = ByteArray(0), info = info.encodeToByteArray(), length = KEY_SIZE)

        /**
         * The counter block AES-CTR starts from: the synthetic IV with the top bits of its two last
         * 32-bit words cleared, as RFC 5297 has it, so that an implementation incrementing only those
         * words counts as one incrementing all 128 bits does.
         */
        private fun ByteArray.toCounter(): ByteArray = copyOf().also { counter ->
            counter[8] = (counter[8].toInt() and 0x7F).toByte()
            counter[12] = (counter[12].toInt() and 0x7F).toByte()
        }

        private fun ByteArray.contentEqualsInConstantTime(other: ByteArray): Boolean {
            if (size != other.size) return false
            var difference = 0
            for (index in indices) {
                difference = difference or (this[index].toInt() xor other[index].toInt())
            }
            return difference == 0
        }
    }
}
