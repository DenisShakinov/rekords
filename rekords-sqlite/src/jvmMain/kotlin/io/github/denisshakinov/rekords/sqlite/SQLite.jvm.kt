@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sqlite

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.RekordsEngine
import io.github.denisshakinov.rekords.core.RekordsEngineConfig
import io.github.denisshakinov.rekords.core.RekordsEngineContainer
import java.io.File

/** The JVM has no SQLite of its own, so this target carries one. */
internal actual fun platformDriver(): SQLiteDriver = BundledSQLiteDriver()

internal actual fun databasePath(name: String): String = File(name).path

/** Registers [SQLite], named in `META-INF/services` for a `ServiceLoader` to find. */
class SQLiteEngineContainer : RekordsEngineContainer {
    override val engine: RekordsEngine<RekordsEngineConfig> = SQLite
}
