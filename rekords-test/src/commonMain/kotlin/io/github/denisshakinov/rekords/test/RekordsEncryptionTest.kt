@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordValue
import io.github.denisshakinov.rekords.core.RekordValues
import io.github.denisshakinov.rekords.core.RekordsCipher
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.addField
import io.github.denisshakinov.rekords.core.getRekord
import io.github.denisshakinov.rekords.core.plus
import io.github.denisshakinov.rekords.core.putRekord
import io.github.denisshakinov.rekords.core.removeField
import io.github.denisshakinov.rekords.core.runOnEditor
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Runs a store encrypting its fields with [cipher] over [rekordsEditor], which is to keep and
 * select what it is handed - ciphertext - the way it does any other text, and to never be handed
 * a value encrypted.
 */
abstract class RekordsEncryptionTest(
    private val rekordsEditor: RekordsEditor,
    private val cipher: RekordsCipher = TestRekordsCipher(),
) {
    private val rekordsStore = RekordsStore(TestSecretRekordsSchema(), rekordsEditor, cipher = cipher)

    private val work = TestSecretTagRekord(id = "work", label = "Work")
    private val home = TestSecretTagRekord(id = "home", label = "Home")

    private val secrets = listOf(
        TestSecretRekord(
            id = 1,
            owner = "alice",
            category = "a",
            note = "Same note",
            pin = 1234,
            unlocked = true,
            rating = 4.5f,
            balance = 10.25,
            created = LocalDate(2024, 1, 15),
            vault = TestSecretVaultRekord(id = 1, code = "1111"),
            tags = listOf(work, home),
            pinnedTag = home,
        ),
        TestSecretRekord(
            id = 2,
            owner = "bob",
            category = "b",
            note = "Same note",
            pin = 0,
            unlocked = false,
            rating = 3f,
            balance = -1.5,
            created = LocalDate(2023, 6, 1),
            vault = TestSecretVaultRekord(id = 2, code = "2222"),
            tags = listOf(home),
            pinnedTag = null,
        ),
        TestSecretRekord(
            id = 3,
            owner = "alice",
            category = "c",
            note = null,
            pin = 42,
            unlocked = true,
            rating = 5f,
            balance = 0.0,
            created = LocalDate(1999, 12, 31),
            vault = TestSecretVaultRekord(id = 1, code = "1111"),
            tags = emptyList(),
            pinnedTag = work,
        ),
    )

    private fun test(block: suspend () -> Unit) = runTest {
        rekordsStore.delete<TestSecretRekord>().getOrThrow()
        rekordsStore.putRekords(secrets).getOrThrow()
        block()
    }

    private suspend fun allSecrets(): List<TestSecretRekord> = rekordsStore
        .getRekords<TestSecretRekord>(orderBy = listOf(Order.Ascending(TestSecretRekord.CATEGORY)))
        .getOrThrow()

    private suspend fun ids(filter: Filter): List<Long> = rekordsStore
        .getRekords<TestSecretRekord>(filter, orderBy = listOf(Order.Ascending(TestSecretRekord.CATEGORY)))
        .getOrThrow()
        .map { it.id }

    /** What [rekordsEditor] itself holds, read past the store and its cipher. */
    private suspend fun storedSecrets(): List<RekordValues> =
        runOnEditor(rekordsEditor) {
            transaction {
                query(
                    TestSecretRekord.REKORD_TYPE,
                    orderBy = listOf(Order.Ascending(TestSecretRekord.CATEGORY)),
                )
            }
        }

    private fun RekordValues.stored(field: String): Any? = (this[field] as? RekordValue.Primitive)?.value

    open fun check_encrypted_rekords_are_read_back_as_written() = test {
        assertEquals(expected = secrets, actual = allSecrets())
    }

    open fun check_encrypted_fields_reach_the_storage_as_ciphertext() = test {
        val stored = storedSecrets()
        assertEquals(expected = secrets.size, actual = stored.size)
        for ((secret, values) in secrets.zip(stored)) {
            assertEquals(expected = secret.category, actual = values.stored(TestSecretRekord.CATEGORY))
            for (field in listOf(
                TestSecretRekord.ID,
                TestSecretRekord.OWNER,
                TestSecretRekord.PIN,
                TestSecretRekord.UNLOCKED,
                TestSecretRekord.RATING,
                TestSecretRekord.BALANCE,
                TestSecretRekord.CREATED,
            )) {
                assertIs<String>(values.stored(field), "$field of ${secret.id} is stored as $values")
            }
            assertTrue(values.values.none { it == RekordValue.Primitive(secret.owner) })
        }
        val (alice, bob, otherAlice) = stored
        // Deterministic: the same value is the same ciphertext.
        assertEquals(alice.stored(TestSecretRekord.OWNER), otherAlice.stored(TestSecretRekord.OWNER))
        assertNotEquals(alice.stored(TestSecretRekord.OWNER), bob.stored(TestSecretRekord.OWNER))
        // Randomized: the same value is a ciphertext of its own each time.
        assertNotEquals(alice.stored(TestSecretRekord.NOTE), bob.stored(TestSecretRekord.NOTE))
        assertEquals(expected = null, actual = otherAlice.stored(TestSecretRekord.NOTE))
    }

    open fun check_deterministic_fields_are_selected_by_equality() = test {
        assertEquals(listOf(1L, 3L), ids(Filter.Equals(TestSecretRekord.OWNER, "alice")))
        assertEquals(listOf(2L), ids(Filter.Not(Filter.Equals(TestSecretRekord.OWNER, "alice"))))
        assertEquals(listOf(1L, 3L), ids(Filter.InList(TestSecretRekord.ID, listOf(1L, 3L, 7L))))
        // An Int compared to a Long field is the same value, as it is unencrypted.
        assertEquals(listOf(2L), ids(Filter.Equals(TestSecretRekord.ID, 2)))
        assertEquals(listOf(3L), ids(Filter.Equals(TestSecretRekord.RATING, 5f)))
        assertEquals(listOf(2L), ids(Filter.Equals(TestSecretRekord.CREATED, LocalDate(2023, 6, 1))))
        assertEquals(
            listOf(3L),
            ids(Filter.Equals(TestSecretRekord.OWNER, "alice") + Filter.Equals(TestSecretRekord.ID, 3L)),
        )
        assertEquals(
            2,
            rekordsStore.count<TestSecretRekord>(Filter.Equals(TestSecretRekord.OWNER, "alice")).getOrThrow(),
        )
    }

    open fun check_nested_encrypted_fields_are_selected_by_equality() = test {
        val inVault = Filter.Nested(TestSecretRekord.VAULT, Filter.Equals(TestSecretVaultRekord.ID, 1))
        assertEquals(listOf(1L, 3L), ids(inVault))
        val withWork = Filter.Nested(TestSecretRekord.TAGS, Filter.Equals(TestSecretTagRekord.ID, "work"))
        assertEquals(listOf(1L), ids(withWork))
        val withHome = Filter.Nested(TestSecretRekord.TAGS, Filter.Equals(TestSecretTagRekord.ID, "home"))
        assertEquals(listOf(1L, 2L), ids(withHome))
        val pinnedWork = Filter.Nested(TestSecretRekord.PINNED_TAG, Filter.Equals(TestSecretTagRekord.ID, "work"))
        assertEquals(listOf(3L), ids(pinnedWork))
    }

    open fun check_randomized_fields_are_selected_by_null() = test {
        assertEquals(listOf(3L), ids(Filter.Equals(TestSecretRekord.NOTE, null)))
    }

    open fun check_rekord_with_encrypted_id_is_updated_not_duplicated() = test {
        val updated = secrets.first().copy(note = "Updated", pin = 9999)
        rekordsStore.putRekord(updated).getOrThrow()

        assertEquals(expected = secrets.size, actual = rekordsStore.count<TestSecretRekord>().getOrThrow())
        assertEquals(
            expected = updated,
            actual = rekordsStore.getRekord<TestSecretRekord>(Filter.Equals(TestSecretRekord.ID, 1L)).getOrThrow(),
        )
    }

    open fun check_encrypted_field_compared_beyond_equality_fails() = test {
        val results = listOf(
            rekordsStore.getRekords<TestSecretRekord>(Filter.Equals(TestSecretRekord.NOTE, "Same note")),
            rekordsStore.getRekords<TestSecretRekord>(Filter.LessThan(TestSecretRekord.ID, 3L)),
            rekordsStore.getRekords<TestSecretRekord>(Filter.Contains(TestSecretRekord.OWNER, "ali")),
            rekordsStore.getRekords<TestSecretRekord>(
                orderBy = listOf(Order.Descending(TestSecretRekord.OWNER)),
            ),
            rekordsStore.count<TestSecretRekord>(
                Filter.InList(TestSecretRekord.UNLOCKED, listOf(true)),
            ),
        )
        for (result in results) {
            assertIs<IllegalArgumentException>(result.exceptionOrNull(), "$result")
        }
    }

    open fun check_transaction_reads_back_what_it_has_encrypted() = test {
        val added = secrets.first().copy(id = 10, owner = "carol")
        val read = rekordsStore.transaction {
            putRekord(added)
            getRekord<TestSecretRekord>(Filter.Equals(TestSecretRekord.OWNER, "carol"))
        }.getOrThrow()
        assertEquals(expected = added, actual = read)
    }

    open fun check_migration_adds_encrypted_fields_with_their_defaults() = test {
        val version = rekordsEditor.schemaEditor.version()
        val upgradedStore = RekordsStore(
            schema = TestSecretRekordsSchema(version = version + 1) {
                removeField<TestSecretRekord>(TestSecretRekord.PIN)
                addField<TestSecretRekord>(TestSecretRekord.PIN, defaultValue = 7)
                removeField<TestSecretRekord>(TestSecretRekord.UNLOCKED)
                addField<TestSecretRekord>(TestSecretRekord.UNLOCKED)
                removeField<TestSecretRekord>(TestSecretRekord.CREATED)
                addField<TestSecretRekord>(TestSecretRekord.CREATED, defaultValue = LocalDate(2000, 1, 1))
            },
            editor = rekordsEditor,
            cipher = cipher,
        )

        val upgraded = upgradedStore
            .getRekords<TestSecretRekord>(orderBy = listOf(Order.Ascending(TestSecretRekord.CATEGORY)))
            .getOrThrow()
        assertEquals(
            expected = secrets.map { it.copy(pin = 7, unlocked = false, created = LocalDate(2000, 1, 1)) },
            actual = upgraded,
        )
        assertTrue(storedSecrets().all { it.stored(TestSecretRekord.PIN) is String })
    }

    open fun check_store_without_cipher_fails_on_encrypted_schema() = runTest {
        val plainStore = RekordsStore(TestSecretRekordsSchema(), rekordsEditor)
        assertIs<IllegalArgumentException>(plainStore.count<TestSecretRekord>().exceptionOrNull())
    }
}
