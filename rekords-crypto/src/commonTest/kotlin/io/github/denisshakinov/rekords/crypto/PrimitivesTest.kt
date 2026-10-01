package io.github.denisshakinov.rekords.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** The primitives each target takes from its own implementation, checked against published vectors. */
class PrimitivesTest {

    /** RFC 4231, test case 2. */
    @Test
    fun hmac_sha256_gives_the_published_tag() {
        val tag = hmacSha256Key("Jefe".encodeToByteArray()).sign("what do ya want for nothing?".encodeToByteArray())
        assertEquals(expected = "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", actual = tag.toHex())
    }

    /** RFC 5869, test case 1. */
    @Test
    fun hkdf_sha256_derives_the_published_key() {
        val key = hkdfSha256(
            inputKey = ByteArray(22) { 0x0b },
            salt = "000102030405060708090a0b0c".hexToByteArray(),
            info = "f0f1f2f3f4f5f6f7f8f9".hexToByteArray(),
            length = 42,
        )
        assertEquals(
            expected = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            actual = key.toHex(),
        )
    }

    /** RFC 5869, test case 3: no salt and no info. */
    @Test
    fun hkdf_sha256_without_salt_derives_the_published_key() {
        val key = hkdfSha256(inputKey = ByteArray(22) { 0x0b }, salt = ByteArray(0), info = ByteArray(0), length = 42)
        assertEquals(
            expected = "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            actual = key.toHex(),
        )
    }

    /** The GCM specification, test case 14: AES-256 with a zero key and IV. */
    @Test
    fun aes_gcm_gives_the_published_ciphertext_and_tag() {
        val key = aesGcmKey(ByteArray(32))
        val ciphertext = key.encrypt(iv = ByteArray(12), plaintext = ByteArray(16), associatedData = ByteArray(0))
        assertEquals(
            expected = "cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919",
            actual = ciphertext.toHex(),
        )
        assertEquals(
            expected = ByteArray(16).toHex(),
            actual = key.decrypt(iv = ByteArray(12), ciphertext = ciphertext, associatedData = ByteArray(0)).toHex(),
        )
    }

    @Test
    fun aes_gcm_refuses_associated_data_it_was_not_given() {
        val key = aesGcmKey(ByteArray(32) { 1 })
        val iv = ByteArray(12) { 1 }
        val ciphertext = key.encrypt(iv, plaintext = ByteArray(16), associatedData = byteArrayOf(1))
        assertFails { key.decrypt(iv, ciphertext = ciphertext, associatedData = byteArrayOf(2)) }
    }

    /**
     * NIST SP 800-38A, F.5.5: CTR-AES256, its two first blocks - the counter carrying from its last
     * byte into the one before as it goes from one to the other.
     */
    @Test
    fun aes_ctr_gives_the_published_ciphertext() {
        val key = aesCtrKey("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4".hexToByteArray())
        val counter = "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff".hexToByteArray()
        val plaintext = "6bc1bee22e409f96e93d7e117393172a" + "ae2d8a571e03ac9c9eb76fac45af8e51"
        val ciphertext = "601ec313775789a5b7a7f504bbf3d228" + "f443e3ca4d62b59aca84e990cacaf5c5"
        assertEquals(expected = ciphertext, actual = key.apply(counter, plaintext.hexToByteArray()).toHex())
        assertEquals(expected = plaintext, actual = key.apply(counter, ciphertext.hexToByteArray()).toHex())
        assertEquals(expected = "", actual = key.apply(counter, ByteArray(0)).toHex())
    }

    @Test
    fun secure_random_bytes_are_as_many_as_asked_for_and_differ() {
        val first = secureRandomBytes(32)
        assertEquals(expected = 32, actual = first.size)
        assertEquals(expected = false, actual = first.contentEquals(secureRandomBytes(32)))
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
