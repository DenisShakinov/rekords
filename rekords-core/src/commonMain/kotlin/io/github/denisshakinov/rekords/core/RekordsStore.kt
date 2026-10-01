@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * Keeps the rekords [schema] declares with [editor].
 *
 * @param cipher encrypts the fields [schema] declares encrypted - see [Encryption] - before
 * [editor] is handed them, and decrypts them as they are read back. A store whose schema encrypts
 * no field needs none; one whose schema does fails every operation without it.
 */
class RekordsStore(
    private val schema: RekordsSchema,
    editor: RekordsEditor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val cipher: RekordsCipher? = null,
) : AutoCloseable {

    private val editor: RekordsEditor =
        if (cipher != null) EncryptingRekordsEditor(editor, cipher) else editor

    init {
        editor.schemaEditor.schema = schema
    }

    private val initMutex = Mutex()
    private val transactionMutex = Mutex()

    @Volatile
    private var initialized = false

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (!initialized) {
                if (cipher == null) {
                    val encryptedFields = schema.encryptedFields()
                    require(encryptedFields.isEmpty()) {
                        "The schema encrypts $encryptedFields, which takes a store given a " +
                            "cipher: RekordsStore(schema, cipher = ...)"
                    }
                }
                // A migration that fails leaves the storage as it found it, version and all, and
                // is tried again by the next operation.
                runOnEditor(editor) { transaction(Migration) }
                initialized = true
            }
        }
    }

    suspend inline fun <reified R> put(
        values: RekordValues,
        filter: Filter? = null,
    ): Result<Unit> = transaction {
        put<R>(values, filter)
    }

    suspend inline fun <reified R> delete(
        filter: Filter? = null
    ): Result<Unit> = transaction {
        delete<R>(filter)
    }

    suspend inline fun <reified R> query(
        fields: List<String>? = null,
        filter: Filter? = null,
        orderBy: List<Order>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): Result<List<RekordValues>> = transaction {
        query<R>(fields, filter, orderBy, limit, offset)
    }

    suspend inline fun <reified R> count(filter: Filter? = null): Result<Int> = transaction {
        count<R>(filter)
    }

    /**
     * Executes multiple rekords store operations atomically.
     * Use [action] with the passed [RekordsEditor] receiver to perform all operations
     * as a single transaction: once [action] throws, none of them is left to be seen.
     * See [RekordsEditor.transaction] for what the transaction guarantees.
     *
     * A failure is returned as the [Result]. A cancellation is not a failure and is thrown on:
     * returned, it would leave the caller's coroutine carrying on as though it had not been
     * cancelled. The transaction is not cut short by it, and may have been committed by then.
     */
    suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): Result<T> =
        try {
            Result.success(
                withContext(dispatcher) {
                    ensureInitialized()
                    transactionMutex.withLock {
                        runOnEditor(editor) { transaction(JoinedAction(action)) }
                    }
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

    suspend inline fun <reified R> putRekord(rekord: R, filter: Filter? = null): Result<Unit> =
        transaction {
            putRekord(rekord, filter)
        }

    suspend inline fun <reified R> putRekords(
        rekords: Iterable<R>,
        filter: Filter? = null,
    ): Result<Unit> = transaction {
        putRekords(rekords, filter)
    }

    suspend inline fun <reified R> deleteRekord(rekord: R): Result<Unit> = transaction {
        deleteRekord(rekord)
    }

    suspend inline fun <reified R> getRekord(filter: Filter): Result<R?> = transaction {
        getRekord<R>(filter)
    }

    suspend inline fun <reified R> getRekords(
        filter: Filter? = null,
        orderBy: List<Order>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): Result<List<R>> = transaction {
        getRekords(filter, orderBy, limit, offset)
    }

    /**
     * Closes the [editor], which the store goes on using: the next operation makes it acquire what
     * it needs again. See [RekordsEditor.close] for when that is worth doing.
     *
     * The schema is initialized once more on that next operation. It costs the one query the
     * version is read with, and it is what a storage that did not survive the close - a database
     * file deleted while it was shut - is built again by.
     */
    override fun close() {
        initialized = false
        editor.close()
    }
}


/**
 * A store keeping its rekords with the engine the target depends on - the one registered, the way
 * a module such as rekords-sqlite registers its engine - configured by [block].
 *
 * Common code creates its store this way and leaves it to each target's dependencies which
 * storage that is. See [RekordsEngine] for how an engine is found.
 *
 * @throws IllegalStateException when the target depends on no engine, or on several: the message
 * tells how to settle it.
 */
fun RekordsStore(
    schema: RekordsSchema,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    cipher: RekordsCipher? = null,
    block: RekordsEngineConfig.() -> Unit = {},
): RekordsStore =
    RekordsStore(schema, selectEngine(RekordsEngines.all()), dispatcher, cipher, block)

/**
 * A store keeping its rekords with [engine], configured by [block], encrypting what [cipher] is
 * given to - see the [RekordsStore] class.
 */
fun <C : RekordsEngineConfig> RekordsStore(
    schema: RekordsSchema,
    engine: RekordsEngine<C>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    cipher: RekordsCipher? = null,
    block: C.() -> Unit = {},
): RekordsStore = RekordsStore(schema, engine.create(block), dispatcher, cipher)

/**
 * The editor a transaction's action is handed. A transaction started on it runs within the one
 * already running, so no editor is ever asked to nest one - each would otherwise nest them in a way
 * of its own, if at all.
 */
private class JoinedTransactionEditor(editor: RekordsEditor) : RekordsEditor by editor {

    override suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): T =
        runOnEditor(this, action)
}

/*
 * The actions below are classes rather than lambdas: an editor restricts the suspending lambdas it
 * runs to its own operations, and handing another editor over to what they run is not one of them.
 */

/** Runs [action] on the editor a transaction hands it, joining any nested one to it. */
private class JoinedAction<T>(
    private val action: suspend RekordsEditor.() -> T,
) : suspend (RekordsEditor) -> T {

    override suspend fun invoke(editor: RekordsEditor): T =
        runOnEditor(JoinedTransactionEditor(editor), action)
}

/** Creates or upgrades the schema of the storage on the editor a transaction hands it. */
private object Migration : suspend (RekordsEditor) -> Unit {

    override suspend fun invoke(editor: RekordsEditor) =
        editor.schemaEditor.init(SchemaMigrationEditor(editor))
}
