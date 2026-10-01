package io.github.denisshakinov.rekords.core

/**
 * Encrypts the fields a schema declares encrypted - see [Encryption] - on their way from a store
 * to its engine, and decrypts them on their way back. Given to a [RekordsStore], it serves every
 * engine alike, since what the engine is handed is ciphertext already.
 *
 * rekords-crypto implements it with AES-GCM; any other implementation is to keep to this:
 *
 * - It is synchronous. It runs inside the transactions of the editor, which suspend on nothing
 *   but the editor's own operations - see [RekordsEditor.transaction] - and an IndexedDB
 *   transaction commits on its own once its code waits on anything else, WebCrypto included.
 * - What [encrypt] gives [decrypt] turns back into the plaintext, whichever way it was encrypted.
 *   Ciphertext that has been tampered with, or that another key encrypted, makes it throw.
 * - Encrypted [deterministically][encrypt], the same plaintext gives the same ciphertext every
 *   time: a filter on such a field is matched by encrypting the value it compares the field to.
 */
interface RekordsCipher {

    /**
     * Encrypts [plaintext].
     *
     * @param deterministic whether the same [plaintext] has to give the same ciphertext every
     * time, as [Encryption.Deterministic] needs. Otherwise it is to give a different one each
     * time, as [Encryption.Randomized] needs.
     */
    fun encrypt(plaintext: ByteArray, deterministic: Boolean): ByteArray

    /**
     * Decrypts what [encrypt] gave, either way it encrypted it.
     *
     * @throws Exception when [ciphertext] is not one [encrypt] gave with this cipher's key.
     */
    fun decrypt(ciphertext: ByteArray): ByteArray
}
