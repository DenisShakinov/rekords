@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.indexeddb

import com.juul.indexeddb.external.Event
import com.juul.indexeddb.logs.Logger
import com.juul.indexeddb.logs.NoOpLogger
import com.juul.indexeddb.logs.Type
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsEngine
import io.github.denisshakinov.rekords.core.RekordsEngineConfig
import io.github.denisshakinov.rekords.core.RekordsEngines
import io.github.denisshakinov.rekords.core.RekordsLogger
import io.github.denisshakinov.rekords.core.debug

/**
 * The engine keeping rekords in the browser's IndexedDB, in the database named by
 * [RekordsEngineConfig.name]. See [IndexedDBRekordsEditor] for how.
 *
 * It registers itself as the module is loaded, so a web target depending on the module has its
 * store created with it:
 * ```kotlin
 * val store = RekordsStore(AppRekordsSchema()) { name = "app" }
 * ```
 */
data object IndexedDB : RekordsEngine<RekordsEngineConfig> {

    override fun create(block: RekordsEngineConfig.() -> Unit): RekordsEditor {
        val config = RekordsEngineConfig().apply(block)
        return IndexedDBRekordsEditor(config.name, config.logger)
    }
}

/** Registers [IndexedDB], as each target does when the module is loaded. */
internal fun registerIndexedDB() = RekordsEngines.register(IndexedDB)

/**
 * Reports what the IndexedDB wrapper logs to [logger], or nothing at all to a logger that drops
 * everything - the messages are then not even built.
 */
internal fun RekordsLogger.asIndexedDBLogger(): Logger =
    if (this === RekordsLogger.None) NoOpLogger else IndexedDBLogger(this)

private class IndexedDBLogger(private val logger: RekordsLogger) : Logger {

    override fun log(type: Type, event: Event?, message: () -> String) =
        logger.debug(TAG, "${type.name}: ${message()}")
}

private const val TAG = "IndexedDB"
