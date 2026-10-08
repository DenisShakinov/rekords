package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.Encryption
import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.delete
import io.github.denisshakinov.rekords.crypto.AesRekordsCipher
import kotlin.reflect.KClass

@Rekord(type = "account")
class AccountRekord(
    @Field(name = ID, id = true)
    val id: Long,
    // Deterministic: the same value encrypts to the same ciphertext, so rekords are still selected
    // by whether the field equals a value.
    @Field(name = EMAIL, searchable = true, encryption = Encryption.Deterministic)
    val email: String,
    // Randomized: encrypted anew each time, selected by nothing but whether it is null.
    @Field(name = BALANCE, encryption = Encryption.Randomized)
    val balance: Double,
    @Field(name = NOTE, encryption = Encryption.Randomized)
    val note: String?,
) {
    override fun toString() = "#$id $email, balance $balance, note $note"

    companion object {
        const val ID = "id"
        const val EMAIL = "email"
        const val BALANCE = "balance"
        const val NOTE = "note"
    }
}

/** [AccountRekord] as the engine keeps it: every encrypted field as the text of its ciphertext. */
@Rekord(type = "account")
class StoredAccountRekord(
    @Field(name = AccountRekord.ID, id = true)
    val id: Long,
    @Field(name = AccountRekord.EMAIL, searchable = true)
    val email: String,
    @Field(name = AccountRekord.BALANCE)
    val balance: String,
    @Field(name = AccountRekord.NOTE)
    val note: String?,
) {
    override fun toString() = "#$id email ${email.take(24)}…, balance ${balance.take(24)}…"
}

class AccountsSchema(rekordType: KClass<*> = AccountRekord::class) : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(rekordType)

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}

/** Fields encrypted by their declaration, with the cipher the store is given. */
suspend fun encryption() {
    section("Encryption")
    // Keeping the key is the application's business: the Android Keystore, the iOS Keychain.
    val key: ByteArray = AesRekordsCipher.generateKey()
    val store = RekordsStore(AccountsSchema(), cipher = AesRekordsCipher(key)) {
        name = "accounts.db"
    }
    store.delete<AccountRekord>().getOrThrow()
    store.putRekords(
        listOf(
            AccountRekord(id = 1, email = "alice@example.com", balance = 120.5, note = "VIP"),
            AccountRekord(id = 2, email = "bob@example.com", balance = 7.0, note = null),
        )
    ).getOrThrow()

    val alice = store
        .getRekord<AccountRekord>(Filter.Equals(AccountRekord.EMAIL, "alice@example.com"))
        .getOrThrow()
    show("Found by the encrypted email", alice)
    // A randomized field still tells whether it is null.
    val withNote = store.count<AccountRekord>(Filter.Not(Filter.Equals(AccountRekord.NOTE, null)))
    show("Accounts with a note", withNote.getOrThrow())

    // An encrypted field cannot be compared by a range, `Filter.Contains` or an order.
    val failed = store.getRekords<AccountRekord>(Filter.Contains(AccountRekord.EMAIL, "example"))
    show("Contains on an encrypted field", failed.exceptionOrNull())
    store.close()

    // The engine is handed nothing but ciphertext.
    val raw = RekordsStore(AccountsSchema(StoredAccountRekord::class)) {
        name = "accounts.db"
    }
    show("What the storage holds", raw.getRekords<StoredAccountRekord>().getOrThrow())
}
