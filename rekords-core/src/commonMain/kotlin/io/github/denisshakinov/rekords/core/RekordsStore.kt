@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass

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

    /**
     * How many transactions writing each rekord type have been committed, which is what an
     * observer is told to read its rekords again by. A type never written is at none.
     */
    private val versions = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** The rekord types each type of [schema] holds, itself included, as deep as they nest. */
    private val heldRekordTypes: Map<String, Set<String>> by lazy {
        schema.rekordTypes.associate { rekordClass ->
            val rekordType = rekordClass.rekordType()
            rekordType to mutableSetOf<String>().also {
                collectHeldRekordTypes(rekordType, rekordClass.allFields(), it)
            }
        }
    }

    private fun heldRekordTypes(rekordType: String): Set<String> =
        heldRekordTypes[rekordType] ?: setOf(rekordType)

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
                        val writes = Writes()
                        val result = runOnEditor(editor) { transaction(JoinedAction(action, writes)) }
                        // Still holding the lock: no transaction reads what this one committed
                        // before its observers are told to read it.
                        invalidate(writes)
                        result
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
     * The rekords of [R] that [filter] selects, ordered by [orderBy] and paged by [limit] and
     * [offset], read again each time a transaction writing them commits - see [observe].
     */
    inline fun <reified R> observeRekords(
        filter: Filter? = null,
        orderBy: List<Order>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): Flow<List<R>> = observeRekordTypes(listOf(rekordType<R>())) {
        getRekords<R>(filter, orderBy, limit, offset)
    }

    /** The first rekord of [R] that [filter] selects, or null - see [observe]. */
    inline fun <reified R> observeRekord(filter: Filter): Flow<R?> =
        observeRekordTypes(listOf(rekordType<R>())) {
            getRekord<R>(filter)
        }

    /** The number of rekords of [R] that [filter] selects - see [observe]. */
    inline fun <reified R> observeCount(filter: Filter? = null): Flow<Int> =
        observeRekordTypes(listOf(rekordType<R>())) {
            count<R>(filter)
        }

    /** The [fields] of the rekords of [R] that [filter] selects, as [query] reads them - see [observe]. */
    inline fun <reified R> observeQuery(
        fields: List<String>? = null,
        filter: Filter? = null,
        orderBy: List<Order>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): Flow<List<RekordValues>> = observeRekordTypes(listOf(rekordType<R>())) {
        query<R>(fields, filter, orderBy, limit, offset)
    }

    /**
     * What [action] reads of the rekords of [rekordClasses], read once the flow is collected and
     * again each time a transaction writing any of them commits.
     *
     * - A rekord type is written by putting rekords of it, or of a type holding it, and by deleting
     *   rekords of it. The types [rekordClasses] hold, as deep as they nest, are observed along
     *   with them: a rekord is read back with what it holds.
     * - Only what this store commits is seen. Another store over the same storage, another process,
     *   writes nothing an observer is told of. A transaction that fails has written nothing.
     * - Several transactions committed while [action] is running are read once it is done, by one
     *   run of it. What it reads may be what it read before: a write is not compared to what is
     *   stored.
     * - [action] runs as a transaction of its own each time, see [transaction]. Its failure ends
     *   the flow with the exception it failed with, to be caught with `catch` or `retry`.
     */
    fun <T> observe(
        vararg rekordClasses: KClass<*>,
        action: suspend RekordsEditor.() -> T,
    ): Flow<T> = observeRekordTypes(rekordClasses.map { it.rekordType() }, action)

    @PublishedApi
    internal fun <T> observeRekordTypes(
        rekordTypes: List<String>,
        action: suspend RekordsEditor.() -> T,
    ): Flow<T> {
        val observedTypes = rekordTypes.flatMapTo(mutableSetOf()) { heldRekordTypes(it) }
        // The versions are a state, so a collector still running the action last read is handed
        // the latest of them when done, however many commits went by meanwhile.
        return versions
            .map { versions -> observedTypes.map { versions[it] ?: 0L } }
            .distinctUntilChanged()
            .map { transaction(action).getOrThrow() }
    }

    /** Tells the observers of what [writes] wrote to read their rekords again. */
    private fun invalidate(writes: Writes) {
        val written = writes.put.flatMapTo(writes.deleted) { heldRekordTypes(it) }
        if (written.isEmpty()) return
        versions.update { versions ->
            versions + written.associateWith { (versions[it] ?: 0L) + 1 }
        }
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

/** Adds to [into] [rekordType] and the rekord types its [fields] hold, as deep as they nest. */
private fun collectHeldRekordTypes(
    rekordType: String,
    fields: List<FieldWithType>,
    into: MutableSet<String>,
) {
    if (!into.add(rekordType)) return
    for (field in fields) {
        when (val type = field.type) {
            is RekordKType -> collectHeldRekordTypes(type.rekordType(), type.allFields(), into)
            is RekordListKType ->
                collectHeldRekordTypes(type.elementRekordType(), type.elementAllFields(), into)
        }
    }
}

/** The rekord types a transaction has put rekords of, and deleted rekords of. */
private class Writes {
    val put: MutableSet<String> = mutableSetOf()
    val deleted: MutableSet<String> = mutableSetOf()
}

/**
 * The editor a transaction's action is handed, keeping [writes]. A transaction started on it runs
 * within the one already running, so no editor is ever asked to nest one - each would otherwise
 * nest them in a way of its own, if at all.
 */
private class JoinedTransactionEditor(
    private val editor: RekordsEditor,
    private val writes: Writes,
) : RekordsEditor by editor {

    override suspend fun put(rekordType: String, values: RekordValues, filter: Filter?) {
        writes.put += rekordType
        val flatFilter = filter?.flattened(rekordType)
        runOnEditor(editor) { put(rekordType, values, flatFilter) }
    }

    override suspend fun delete(rekordType: String, filter: Filter?) {
        writes.deleted += rekordType
        val flatFilter = filter?.flattened(rekordType)
        runOnEditor(editor) { delete(rekordType, flatFilter) }
    }

    override suspend fun query(
        rekordType: String,
        fields: List<String>?,
        filter: Filter?,
        orderBy: List<Order>?,
        limit: Int?,
        offset: Int?,
    ): List<RekordValues> {
        val flatFilter = filter?.flattened(rekordType)
        return runOnEditor(editor) { query(rekordType, fields, flatFilter, orderBy, limit, offset) }
    }

    override suspend fun count(rekordType: String, filter: Filter?): Int {
        val flatFilter = filter?.flattened(rekordType)
        return runOnEditor(editor) { count(rekordType, flatFilter) }
    }

    /** See [Embedded]: a storage is asked about the fields of an embedded class as the rekord's. */
    private fun Filter.flattened(rekordType: String): Filter =
        if (editor.schemaEditor.hasSchema) flattened(rekordType, editor.schemaEditor.schema) else this

    override suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): T =
        runOnEditor(this, action)
}

/*
 * The actions below are classes rather than lambdas: an editor restricts the suspending lambdas it
 * runs to its own operations, and handing another editor over to what they run is not one of them.
 */

/**
 * Runs [action] on the editor a transaction hands it, joining any nested one to it, and keeps in
 * [writes] what it writes.
 */
private class JoinedAction<T>(
    private val action: suspend RekordsEditor.() -> T,
    private val writes: Writes,
) : suspend (RekordsEditor) -> T {

    override suspend fun invoke(editor: RekordsEditor): T =
        runOnEditor(JoinedTransactionEditor(editor, writes), action)
}

/** Creates or upgrades the schema of the storage on the editor a transaction hands it. */
private object Migration : suspend (RekordsEditor) -> Unit {

    override suspend fun invoke(editor: RekordsEditor) =
        editor.schemaEditor.init(SchemaMigrationEditor(editor))
}
