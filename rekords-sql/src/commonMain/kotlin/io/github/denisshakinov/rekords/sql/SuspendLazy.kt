package io.github.denisshakinov.rekords.sql

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Computes [initializer] on first use and hands out the same value afterwards.
 *
 * A plain `by lazy` cannot be used because opening a connection suspends, and the value has to be
 * produced at most once even when several coroutines ask for it at the same time.
 */
internal class SuspendLazy<T : Any>(private val initializer: suspend () -> T) {

    private val mutex = Mutex()

    @Volatile
    private var value: T? = null

    suspend operator fun invoke(): T =
        value ?: mutex.withLock { value ?: initializer().also { value = it } }

    /**
     * Gives up the value held and returns it, so that the next [invoke] computes a new one.
     *
     * Not guarded by the mutex, which would have to be waited on and so could not be done without
     * suspending: the caller is the one that has to keep this off the operations using the value.
     */
    fun reset(): T? = value.also { value = null }
}
