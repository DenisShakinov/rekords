@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Reads and writes rekords, and is what a storage implements to keep them.
 *
 * Restricts suspension: code running on an editor - an action given to [transaction], a schema's
 * migration, an extension such as [putRekord] - can suspend on nothing but the editor's own
 * operations. See [transaction] for why.
 */
@RestrictsSuspension
interface RekordsEditor : AutoCloseable {

    /** Provides access to the schema editor for managing rekord types and fields. */
    @InternalRekordsApi
    val schemaEditor: RekordsSchemaEditor

    /**
     * Inserts or updates a map of [values] with the specified [rekordType] and [filter].
     *
     * @param rekordType the type of rekords to insert or update.
     * @param values the map of field values to insert or update.
     * @param filter indicates which rekords should be updated.
     * If null, updates all rekords with the same [rekordType].
     */
    suspend fun put(
        rekordType: String,
        values: RekordValues,
        filter: Filter? = null,
    )

    /**
     * Deletes rekords with the specified [rekordType] and [filter].
     *
     * @param rekordType the type of rekords to delete.
     * @param filter indicates which rekords should be deleted.
     * If null, deletes all rekords with the same [rekordType].
     */
    suspend fun delete(rekordType: String, filter: Filter? = null)

    /**
     * Queries the rekords store and returns the rekords found.
     *
     * @param rekordType the rekord type name to find rekords of specified type.
     * @param fields the list of fields to return. Passing null will return all fields, which is discouraged.
     * @param filter filters which rekords to return. Passing null will return all rekords.
     * @param orderBy declares how to order the rekords.
     * Passing null will use the default sort order, which may be unordered.
     * @param limit limits the number of rekords returned by the query.
     * @param offset specifies the starting index.
     * @return the list of matching rekords as [RekordValues].
     */
    suspend fun query(
        rekordType: String,
        fields: List<String>? = null,
        filter: Filter? = null,
        orderBy: List<Order>? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<RekordValues>

    /**
     * Counts rekords in the rekords store.
     *
     * @param rekordType the rekord type name to count rekords of specified type.
     * @param filter filters which rekords to count. Passing null will count all rekords.
     * @return the number of matching rekords.
     */
    suspend fun count(rekordType: String, filter: Filter? = null): Int

    /**
     * Runs [action] as one transaction, and is what every implementation has to answer the same
     * way, since the store counts on nothing more than this:
     *
     * - What [action] does takes effect whole or not at all. Once it throws - a cancellation
     *   included - no change it made is left to be seen, and the exception goes on to the caller.
     * - Inside [action], what it has written is what it reads back.
     * - Nothing but the editor's operations is suspended on in [action], which the editor
     *   restricts it to. A storage whose transactions end once the code running them waits on
     *   anything else - IndexedDB's do - would otherwise commit halfway, and no longer be able to
     *   take back what came before. What [action] needs from elsewhere is fetched before it runs.
     * - A transaction runs to its end. [action] runs outside the caller's coroutine context, as
     *   code an editor restricts does, so nothing can cancel it halfway: a caller cancelled
     *   meanwhile sees the cancellation once the transaction has been committed or rolled back.
     *
     * How that is got is the implementation's own business: a database has transactions of its
     * own, and an editor without them keeps what it takes to undo the changes made.
     *
     * The store runs one transaction at a time and never calls this while another is running.
     * The editor it hands [action] joins a nested call to the one running instead, so a nested
     * transaction is not rolled back on its own - its failure fails the transaction it is in.
     *
     * @param action the operations to run, given the editor to run them on.
     * @return what [action] returns.
     */
    suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): T

    /**
     * Releases whatever the editor holds open - a database connection, say.
     *
     * The editor stays usable afterwards: an implementation that needs a resource acquires it
     * again on the next call. Nothing has to be closed to end the process either, since the
     * platform reclaims what a dead process held; closing is for the times a store is given up
     * while the process lives on - a test, or a window that is done with its database.
     *
     * Not to be called while operations are in flight. An operation that has already been handed
     * the resource keeps using it, and closing it underneath is the caller's own doing.
     */
    override fun close()
}

/**
 * Runs [action] on [editor] from code its restriction leaves out - a store, an editor's own
 * members - which may not call a restricted function directly.
 *
 * [action] starts right away, on the calling thread. It runs with no coroutine context, which is
 * what a restricted coroutine is required to have: no job to cancel it and no dispatcher, so what
 * it suspends on resumes it wherever that happens to be. Its result goes back to the caller through
 * the caller's own dispatcher.
 */
@InternalRekordsApi
suspend fun <E : RekordsEditor, T> runOnEditor(editor: E, action: suspend E.() -> T): T =
    suspendCoroutineUninterceptedOrReturn { continuation ->
        action.startCoroutineUninterceptedOrReturn(editor, RestrictedCompletion(continuation))
    }

/** Hands what a restricted coroutine ends with to [continuation], through its dispatcher. */
private class RestrictedCompletion<T>(
    private val continuation: Continuation<T>,
) : Continuation<T> {

    override val context: CoroutineContext get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<T>) = continuation.intercepted().resumeWith(result)
}

@InternalRekordsApi
fun RekordsEditor.rekordSchema(rekordType: String): Map<String, FieldWithType> =
    schemaEditor.rekordSchema(rekordType)

suspend inline fun <reified R> RekordsEditor.put(
    values: RekordValues,
    filter: Filter? = null,
) = put(rekordType<R>(), values, filter)

suspend inline fun <reified R> RekordsEditor.putRekord(rekord: R, filter: Filter? = null) {
    val values: RekordValues = encode(rekord)
    return put<R>(
        values = values,
        filter = idsFilter(values, idFields<R>()) + filter,
    )
}

suspend inline fun <reified R> RekordsEditor.putRekords(
    rekords: Iterable<R>,
    filter: Filter? = null,
) {
    for (rekord: R in rekords) {
        putRekord<R>(rekord, filter)
    }
}

suspend inline fun <reified R> RekordsEditor.delete(filter: Filter? = null) =
    delete(rekordType<R>(), filter)

suspend inline fun <reified R> RekordsEditor.deleteRekord(rekord: R) =
    delete<R>(filter = idsFilter(encode(rekord), idFields<R>()))

suspend inline fun <reified R> RekordsEditor.query(
    fields: List<String>? = null,
    filter: Filter? = null,
    orderBy: List<Order>? = null,
    limit: Int? = null,
    offset: Int? = null,
): List<RekordValues> = query(rekordType<R>(), fields, filter, orderBy, limit, offset)

suspend inline fun <reified R> RekordsEditor.getRekord(filter: Filter): R? =
    query<R>(
        filter = filter,
        limit = 1,
    ).firstOrNull()?.decode<R>()

suspend inline fun <reified R> RekordsEditor.getRekords(
    filter: Filter? = null,
    orderBy: List<Order>? = null,
    limit: Int? = null,
    offset: Int? = null,
): List<R> = query<R>(
    filter = filter,
    orderBy = orderBy,
    limit = limit,
    offset = offset,
).map { it.decode() }

suspend inline fun <reified R> RekordsEditor.count(filter: Filter? = null): Int =
    count(rekordType<R>(), filter)

@InternalRekordsApi
fun idsFilter(values: RekordValues, idFields: List<String>): Filter {
    if (idFields.size == 1) {
        return equalsFilter(values, idFields.first())
    }
    return Filter.And(idFields.map { field -> equalsFilter(values, field) })
}

private fun equalsFilter(values: RekordValues, field: String): Filter {
    val value = (values[field] as? RekordValue.Primitive)?.value
    return Filter.Equals(field, value = value)
}