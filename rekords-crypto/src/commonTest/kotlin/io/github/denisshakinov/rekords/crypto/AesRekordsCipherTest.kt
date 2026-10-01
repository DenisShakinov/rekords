package io.github.denisshakinov.rekords.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AesRekordsCipherTest {

    private val key = ByteArray(AesRekordsCipher.KEY_SIZE) { it.toByte() }
    private val cipher = AesRekordsCipher(key)

    private val plaintexts = listOf(
        ByteArray(0),
        byteArrayOf(1),
        "Some secret".encodeToByteArray(),
        ByteArray(1000) { (it * 7).toByte() },
    )

    @Test
    fun what_is_encrypted_either_way_is_decrypted_back() {
        for (plaintext in plaintexts) {
            for (deterministic in listOf(true, false)) {
                assertContentEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext, deterministic)))
            }
        }
    }

    @Test
    fun deterministic_encryption_gives_the_same_ciphertext_for_the_same_plaintext() {
        val plaintext = "alice".encodeToByteArray()
        assertContentEquals(cipher.encrypt(plaintext, deterministic = true), cipher.encrypt(plaintext, deterministic = true))
        assertContentEquals(
            cipher.encrypt(plaintext, deterministic = true),
            AesRekordsCipher(key.copyOf()).encrypt(plaintext, deterministic = true),
        )
        assertFalse(
            cipher.encrypt(plaintext, deterministic = true)
                .contentEquals(cipher.encrypt("bob".encodeToByteArray(), deterministic = true))
        )
    }

    @Test
    fun randomized_encryption_gives_a_ciphertext_of_its_own_each_time() {
        val plaintext = "alice".encodeToByteArray()
        assertFalse(cipher.encrypt(plaintext, deterministic = false).contentEquals(cipher.encrypt(plaintext, deterministic = false)))
    }

    /** Every target gives these bytes, which a storage written on one has to be read with on another. */
    @Test
    fun deterministic_ciphertext_is_the_same_on_every_target() {
        assertEquals(
            expected = DETERMINISTIC_CIPHERTEXT_OF_REKORDS,
            actual = cipher.encrypt("rekords".encodeToByteArray(), deterministic = true).toHex(),
        )
    }

    @Test
    fun ciphertext_is_the_mode_the_iv_and_what_is_encrypted() {
        val plaintext = "rekords".encodeToByteArray()
        val randomized = cipher.encrypt(plaintext, deterministic = false)
        val deterministic = cipher.encrypt(plaintext, deterministic = true)
        assertEquals(expected = 1 + 12 + plaintext.size + 16, actual = randomized.size)
        assertEquals(expected = 1, actual = randomized[0].toInt())
        assertEquals(expected = 1 + 16 + plaintext.size, actual = deterministic.size)
        assertEquals(expected = 2, actual = deterministic[0].toInt())
    }

    @Test
    fun tampered_ciphertext_is_refused() {
        for (deterministic in listOf(true, false)) {
            val ciphertext = cipher.encrypt("Some secret".encodeToByteArray(), deterministic)
            for (index in ciphertext.indices) {
                val tampered = ciphertext.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
                assertFails("byte $index tampered with, deterministic: $deterministic") { cipher.decrypt(tampered) }
            }
            assertFails { cipher.decrypt(ciphertext.copyOf(ciphertext.size - 1)) }
        }
        assertFails { cipher.decrypt(ByteArray(0)) }
    }

    /** A ciphertext passed off as one of the other mode - a randomized one left to be compared, say. */
    @Test
    fun ciphertext_of_another_mode_is_refused() {
        val randomized = cipher.encrypt("Some secret".encodeToByteArray(), deterministic = false)
        assertFails { cipher.decrypt(randomized.copyOf().also { it[0] = 2 }) }
        val deterministic = cipher.encrypt("Some secret, long enough".encodeToByteArray(), deterministic = true)
        assertFails { cipher.decrypt(deterministic.copyOf().also { it[0] = 1 }) }
    }

    @Test
    fun ciphertext_of_another_key_is_refused() {
        val other = AesRekordsCipher(AesRekordsCipher.generateKey())
        assertFails { cipher.decrypt(other.encrypt("Some secret".encodeToByteArray(), deterministic = true)) }
        assertFails { cipher.decrypt(other.encrypt("Some secret".encodeToByteArray(), deterministic = false)) }
    }

    @Test
    fun key_of_another_size_is_refused() {
        assertFailsWith<IllegalArgumentException> { AesRekordsCipher(ByteArray(16)) }
    }

    @Test
    fun generated_keys_are_of_the_size_a_key_is_and_differ() {
        val first = AesRekordsCipher.generateKey()
        assertEquals(expected = AesRekordsCipher.KEY_SIZE, actual = first.size)
        assertFalse(first.contentEquals(AesRekordsCipher.generateKey()))
    }

    private companion object {
        /** "rekords" encrypted deterministically under the key of the bytes 0 to 31. */
        const val DETERMINISTIC_CIPHERTEXT_OF_REKORDS = "027d7c3eca485690968ee024960bd52308f4db26bb4c89be"
    }
}
