package io.github.denisshakinov.rekords.sqlite

import io.github.denisshakinov.rekords.test.RekordsEncryptionTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/** Runs the shared encryption suite against SQLite, through the bundled driver the JVM has. */
class JvmSQLiteRekordsEncryptionTest : RekordsEncryptionTest(
    SQLite.create { name = temporaryEncryptionDatabaseFile().path }
) {

    @Test
    override fun check_encrypted_rekords_are_read_back_as_written() = super.check_encrypted_rekords_are_read_back_as_written()

    @Test
    override fun check_encrypted_fields_reach_the_storage_as_ciphertext() = super.check_encrypted_fields_reach_the_storage_as_ciphertext()

    @Test
    override fun check_deterministic_fields_are_selected_by_equality() = super.check_deterministic_fields_are_selected_by_equality()

    @Test
    override fun check_nested_encrypted_fields_are_selected_by_equality() = super.check_nested_encrypted_fields_are_selected_by_equality()

    @Test
    override fun check_randomized_fields_are_selected_by_null() = super.check_randomized_fields_are_selected_by_null()

    @Test
    override fun check_rekord_with_encrypted_id_is_updated_not_duplicated() = super.check_rekord_with_encrypted_id_is_updated_not_duplicated()

    @Test
    override fun check_encrypted_field_compared_beyond_equality_fails() = super.check_encrypted_field_compared_beyond_equality_fails()

    @Test
    override fun check_transaction_reads_back_what_it_has_encrypted() = super.check_transaction_reads_back_what_it_has_encrypted()

    @Test
    override fun check_migration_adds_encrypted_fields_with_their_defaults() = super.check_migration_adds_encrypted_fields_with_their_defaults()

    @Test
    override fun check_store_without_cipher_fails_on_encrypted_schema() = super.check_store_without_cipher_fails_on_encrypted_schema()
}

private fun temporaryEncryptionDatabaseFile(): File =
    Files.createTempDirectory("rekords").toFile().also { it.deleteOnExit() }.resolve("encryption.db")
