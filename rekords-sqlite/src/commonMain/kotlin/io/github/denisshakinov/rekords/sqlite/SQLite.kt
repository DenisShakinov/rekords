package io.github.denisshakinov.rekords.sqlite

import androidx.sqlite.SQLiteDriver
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsEngine
import io.github.denisshakinov.rekords.core.RekordsEngineConfig

/**
 * The engine keeping rekords in a SQLite database named by [RekordsEngineConfig.name], through
 * the SQLite each target ships:
 *
 * - Android: the database directory of the application, through the SQLite of the framework.
 * - Apple and Linux: the directory the platform keeps application data in, through the SQLite of
 *   the operating system - which the final binary has to link, see [linking](#linking).
 * - JVM: the path the name is, relative to the working directory, through the SQLite bundled with
 *   `androidx.sqlite:sqlite-bundled`.
 *
 * It registers itself on every target the module is built for, so a target depending on the
 * module has its store created with it:
 * ```kotlin
 * val store = RekordsStore(AppRekordsSchema()) { name = "app.db" }
 * ```
 *
 * ## Linking
 * A klib carries no linker options, so whoever builds a native binary has to link the system's
 * SQLite themselves. Where Kotlin performs the link - a test executable, a dynamic framework -
 * `linkerOpts.add("-lsqlite3")` does it. A static framework is only an archive, and the app is
 * linked by Xcode, which the option never reaches: there the flag belongs in the target's
 * `OTHER_LDFLAGS`.
 */
data object SQLite : RekordsEngine<SQLiteConfig> {

    override fun create(block: SQLiteConfig.() -> Unit): RekordsEditor {
        val config = SQLiteConfig().apply(block)
        return SQLiteRekordsEditor(
            driver = config.driver ?: platformDriver(),
            databasePath = databasePath(config.name),
            logger = config.logger,
        )
    }
}

/** What [SQLite] is configured with. */
class SQLiteConfig : RekordsEngineConfig() {

    /**
     * Opens the database in place of the SQLite the target ships - `BundledSQLiteDriver` on
     * Android, say, when the one built into the OS is not wanted. Its artifact has to be on the
     * classpath.
     */
    var driver: SQLiteDriver? = null
}

/** The driver of the SQLite the target ships. */
internal expect fun platformDriver(): SQLiteDriver

/** Where the database [name] names is kept on the target. */
internal expect fun databasePath(name: String): String
