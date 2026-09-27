@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sqlite

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.RekordsEngine
import io.github.denisshakinov.rekords.core.RekordsEngineConfig
import io.github.denisshakinov.rekords.core.RekordsEngineContainer

internal actual fun platformDriver(): SQLiteDriver = AndroidSQLiteDriver()

internal actual fun databasePath(name: String): String =
    RekordsContextProvider.applicationContext.getDatabasePath(name).path

/** Registers [SQLite], named in `META-INF/services` for a `ServiceLoader` to find. */
class SQLiteEngineContainer : RekordsEngineContainer {
    override val engine: RekordsEngine<RekordsEngineConfig> = SQLite
}

/**
 * Hands the application context over to [SQLite], which needs it to find the database directory,
 * so that nothing has to be passed to create a store.
 *
 * Declared in the module's manifest, which the application's own is merged with, so it is created
 * as the application starts - before anything could ask for a store. An application that removes
 * it from its manifest is left with no context to open a database in.
 */
internal class RekordsContextProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { applicationContext = it.applicationContext }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {

        private var context: Context? = null

        var applicationContext: Context
            get() = checkNotNull(context) {
                "The application context was not handed over to rekords: RekordsContextProvider " +
                    "has to stay in the application's merged manifest."
            }
            private set(value) {
                context = value
            }
    }
}
