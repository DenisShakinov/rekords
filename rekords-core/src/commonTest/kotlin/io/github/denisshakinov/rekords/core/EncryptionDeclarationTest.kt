@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import io.github.denisshakinov.rekords.test.TestSecretRekord
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class EncryptionDeclarationTest {

    @Test
    fun encrypted_field_is_stored_as_text_and_keeps_its_type() {
        val fields = allFields<TestSecretRekord>().associateBy { it.field.name }

        val pin = assertIs<EncryptedKType>(fields.getValue(TestSecretRekord.PIN).type)
        assertEquals(expected = String::class, actual = pin.classifier)
        assertEquals(expected = typeOf<Int>(), actual = pin.plainType)
        assertEquals(expected = Encryption.Randomized, actual = pin.encryption)

        val note = assertIs<EncryptedKType>(fields.getValue(TestSecretRekord.NOTE).type)
        assertEquals(expected = true, actual = note.isMarkedNullable)

        // A date is encrypted as the number it is stored as.
        val created = assertIs<EncryptedKType>(fields.getValue(TestSecretRekord.CREATED).type)
        assertEquals(expected = typeOf<Long>(), actual = created.plainType)

        assertEquals(expected = typeOf<String>(), actual = fields.getValue(TestSecretRekord.CATEGORY).type)
    }

    @Test
    fun randomized_id_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<RandomizedIdRekord>() }
    }

    @Test
    fun randomized_searchable_field_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<RandomizedSearchableRekord>() }
    }

    @Test
    fun encrypted_rekord_field_is_refused() {
        assertFailsWith<IllegalArgumentException> { allFields<EncryptedCompositeRekord>() }
    }
}

@Rekord(type = "randomized_id")
private class RandomizedIdRekord(
    @Field(name = "id", id = true, encryption = Encryption.Randomized)
    val id: Long,
)

@Rekord(type = "randomized_searchable")
private class RandomizedSearchableRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "name", searchable = true, encryption = Encryption.Randomized)
    val name: String,
)

@Rekord(type = "encrypted_composite")
private class EncryptedCompositeRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "inner", encryption = Encryption.Deterministic)
    val inner: RandomizedSearchableRekord,
)
