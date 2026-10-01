package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Encryption
import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.test.TestSecretRekord.Companion.REKORD_TYPE
import kotlinx.datetime.LocalDate

/** A rekord encrypting a field of every type a field can hold, either way it can be encrypted. */
@Rekord(type = REKORD_TYPE)
data class TestSecretRekord(
    @Field(name = ID, id = true, encryption = Encryption.Deterministic)
    val id: Long,
    @Field(name = OWNER, searchable = true, encryption = Encryption.Deterministic)
    val owner: String,
    /** Not encrypted, so rekords can be ordered by it. */
    @Field(name = CATEGORY)
    val category: String,
    @Field(name = NOTE, encryption = Encryption.Randomized)
    val note: String?,
    @Field(name = PIN, encryption = Encryption.Randomized)
    val pin: Int,
    @Field(name = UNLOCKED, encryption = Encryption.Randomized)
    val unlocked: Boolean,
    @Field(name = RATING, encryption = Encryption.Deterministic)
    val rating: Float,
    @Field(name = BALANCE, encryption = Encryption.Randomized)
    val balance: Double,
    @Field(name = CREATED, encryption = Encryption.Deterministic)
    val created: LocalDate,
    @Field(name = VAULT)
    val vault: TestSecretVaultRekord,
    @Field(name = TAGS)
    val tags: List<TestSecretTagRekord>,
    /** Of the type the list holds, and nullable, so that each is told from the other and from none. */
    @Field(name = PINNED_TAG)
    val pinnedTag: TestSecretTagRekord?,
) {
    companion object {
        const val REKORD_TYPE = "secret"
        const val ID = "id"
        const val OWNER = "owner"
        const val CATEGORY = "category"
        const val NOTE = "note"
        const val PIN = "pin"
        const val UNLOCKED = "unlocked"
        const val RATING = "rating"
        const val BALANCE = "balance"
        const val CREATED = "created"
        const val VAULT = "vault"
        const val TAGS = "tags"
        const val PINNED_TAG = "pinned_tag"
    }
}

/** A rekord nested in [TestSecretRekord], its id encrypted as well. */
@Rekord(type = "secret_tag")
data class TestSecretTagRekord(
    @Field(name = ID, id = true, encryption = Encryption.Deterministic)
    val id: String,
    @Field(name = LABEL, encryption = Encryption.Randomized)
    val label: String,
) {
    companion object {
        const val ID = "id"
        const val LABEL = "label"
    }
}

/** A rekord nested in [TestSecretRekord] on its own, its id encrypted as well. */
@Rekord(type = "secret_vault")
data class TestSecretVaultRekord(
    @Field(name = ID, id = true, encryption = Encryption.Deterministic)
    val id: Int,
    @Field(name = CODE, encryption = Encryption.Randomized)
    val code: String,
) {
    companion object {
        const val ID = "id"
        const val CODE = "code"
    }
}
