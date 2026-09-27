@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.WriteTransaction
import com.juul.indexeddb.openDatabase
import io.github.denisshakinov.rekords.core.FieldWithType
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.RekordValues
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsLogger
import io.github.denisshakinov.rekords.core.RekordsSchemaEditor
import io.github.denisshakinov.rekords.core.rekordSchema
import io.github.denisshakinov.rekords.core.runOnEditor
import kotlin.reflect.KClass

/**
 * A [RekordsEditor] storing rekords in the browser's IndexedDB, in the database named
 * [databaseName] of the page's origin - or of the worker's, where it runs in one. [logger] receives
 * what IndexedDB is asked, each request and transaction.
 *
 * The database holds the same object stores whatever the schema is: one for every rekord, one for
 * the indexes kept of them and one for what the editor keeps about itself. A change to the schema
 * is then a change to the rekords alone, run in the transaction the store runs a migration in, and
 * rolled back with it. IndexedDB's own schema, which only a transaction of its own can change, is
 * never changed after the database is created.
 *
 * Every transaction is a readwrite one over all three stores, which IndexedDB runs one at a time
 * across every page of the origin holding the database open.
 *
 * The database is opened on first use and kept open for as long as the editor is. [close] gives it
 * up, and the editor carries on: the next operation opens it again.
 *
 * Operations are not serialized here, so the editor has to be given to a single
 * [io.github.denisshakinov.rekords.core.RekordsStore], which runs one transaction at a time, and reached
 * through it alone.
 */
class IndexedDBRekordsEditor(
    private val databaseName: String,
    private val logger: RekordsLogger = RekordsLogger.None,
) : RekordsEditor {

    // The editor restricts suspension to its own operations, which a class nested in it - the
    // schema editor - would not be let call. What both reach the database through lives here.
    private val connection = IndexedDBConnection(databaseName, logger) { fields(it) }

    override val schemaEditor: RekordsSchemaEditor = IndexedDBSchemaEditor(connection)

    override fun close() = connection.close()

    override suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): T =
        connection.transaction { runOnEditor(this, action) }

    override suspend fun put(rekordType: String, values: RekordValues, filter: Filter?) =
        connection.session { put(rekordType, values, filter) }

    override suspend fun delete(rekordType: String, filter: Filter?) =
        connection.session { delete(rekordType, filter) }

    override suspend fun query(
        rekordType: String,
        fields: List<String>?,
        filter: Filter?,
        orderBy: List<Order>?,
        limit: Int?,
        offset: Int?,
    ): List<RekordValues> = connection.session { query(rekordType, filter, orderBy, limit, offset) }

    override suspend fun count(rekordType: String, filter: Filter?): Int =
        connection.session { count(rekordType, filter) }

    /**
     * The fields of [rekordType]. Only a store sets a schema, so an editor reached on its own has
     * none to read, and keeps every rekord for scanning alone - as the in-memory editor does.
     */
    private fun fields(rekordType: String): Map<String, FieldWithType> =
        if (schemaEditor.hasSchema) schemaEditor.rekordSchema(rekordType) else emptyMap()

    private class IndexedDBSchemaEditor(
        private val connection: IndexedDBConnection,
    ) : RekordsSchemaEditor() {

        override suspend fun version(): Int = connection.session { version() }

        override suspend fun updateVersion(newVersion: Int) = connection.session { updateVersion(newVersion) }

        /** Every rekord type is kept in the same object store, so there is nothing to create. */
        override suspend fun addRekordType(rekordClass: KClass<*>) = Unit

        override suspend fun removeRekordType(rekordType: String) =
            connection.session { removeRekordType(rekordType) }

        override suspend fun renameRekordType(oldRekordType: String, newRekordType: String) =
            connection.session { renameRekordType(oldRekordType, newRekordType) }

        override suspend fun addField(
            rekordType: String,
            field: FieldWithType,
            defaultValue: PrimitiveRekordValue?,
        ) = connection.session { addField(rekordType, field, defaultValue) }

        override suspend fun removeField(rekordType: String, fieldName: String) =
            connection.session { removeField(rekordType, fieldName) }

        override suspend fun renameField(rekordType: String, oldFieldName: String, newFieldName: String) =
            connection.session { renameField(rekordType, oldFieldName, newFieldName) }
    }

}

/**
 * The database an [IndexedDBRekordsEditor] keeps its rekords in, and the transaction running on
 * it.
 */
private class IndexedDBConnection(
    private val databaseName: String,
    logger: RekordsLogger,
    private val fields: (rekordType: String) -> Map<String, FieldWithType>,
) {

    private val logger = logger.asIndexedDBLogger()

    private var database: Database? = null

    /** The transaction running, which every operation of it is run within. */
    private var transaction: WriteTransaction? = null

    fun close() {
        database?.close()
        database = null
    }

    suspend fun <T> transaction(action: suspend () -> T): T =
        database().writeTransaction(REKORDS_STORE, SEARCH_STORE, META_STORE) {
            transaction = this
            try {
                action()
            } finally {
                transaction = null
            }
        }

    /**
     * Runs [operation] within the transaction running, or within one of its own when the editor is
     * reached outside of any - the way a SQL statement runs where no transaction was begun.
     */
    suspend fun <T> session(operation: suspend RekordsSession.() -> T): T {
        transaction?.let { return RekordsSession(it, fields).operation() }
        return database().writeTransaction(REKORDS_STORE, SEARCH_STORE, META_STORE) {
            RekordsSession(this, fields).operation()
        }
    }

    private suspend fun database(): Database =
        database ?: openDatabase(databaseName, DATABASE_VERSION, logger) { database, oldVersion, _ ->
            if (oldVersion < 1) {
                database.createObjectStore(REKORDS_STORE)
                database.createObjectStore(SEARCH_STORE)
                database.createObjectStore(META_STORE)
            }
        }.also { database = it }

    private companion object {
        /** IndexedDB's own version of the database, which the object stores are created at. */
        const val DATABASE_VERSION = 1
    }
}
