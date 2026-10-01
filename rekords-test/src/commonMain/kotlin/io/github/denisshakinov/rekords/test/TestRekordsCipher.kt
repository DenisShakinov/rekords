package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.RekordsCipher
import kotlin.random.Random

/**
 * A cipher keeping to what [RekordsCipher] asks and to nothing a real one is for: what it encrypts
 * is no secret, only bytes other than the plaintext, which it gives back. Encrypted randomized,
 * the same plaintext gives a ciphertext of its own each time.
 */
class TestRekordsCipher : RekordsCipher {

    override fun encrypt(plaintext: ByteArray, deterministic: Boolean): ByteArray {
        val nonce = if (deterministic) ByteArray(NONCE_SIZE) else Random.nextBytes(NONCE_SIZE)
        val mode = if (deterministic) DETERMINISTIC else RANDOMIZED
        return byteArrayOf(mode) + nonce + plaintext.masked(nonce)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.first() == DETERMINISTIC || ciphertext.first() == RANDOMIZED) {
            "Not a ciphertext of TestRekordsCipher"
        }
        val nonce = ciphertext.copyOfRange(1, 1 + NONCE_SIZE)
        return ciphertext.copyOfRange(1 + NONCE_SIZE, ciphertext.size).masked(nonce)
    }

    private fun ByteArray.masked(nonce: ByteArray): ByteArray =
        ByteArray(size) { index -> (this[index].toInt() xor (nonce[index % NONCE_SIZE] + MASK + index)).toByte() }

    private companion object {
        const val NONCE_SIZE = 8
        const val MASK = 0x5A
        const val DETERMINISTIC: Byte = 1
        const val RANDOMIZED: Byte = 2
    }
}
