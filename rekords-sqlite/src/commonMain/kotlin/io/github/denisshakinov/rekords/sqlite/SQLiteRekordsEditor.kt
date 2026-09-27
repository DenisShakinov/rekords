package io.github.denisshakinov.rekords.sqlite

import androidx.sqlite.SQLiteDriver
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsLogger
import io.github.denisshakinov.rekords.sql.SQLRekordsEditor
import io.github.denisshakinov.rekords.sql.withLogging

/**
 * A [RekordsEditor] storing rekords in the SQLite database at [databasePath], reached through
 * [driver].
 *
 * The [SQLite] engine creates one with the driver the target ships and the directory it keeps
 * databases in. This is for an editor that needs neither - a database at a path of its own.
 *
 * @param driver opens connections to the database; its artifact has to be on the classpath.
 * @param databasePath the full path of the database file.
 * @param logger receives every statement the editor runs.
 */
fun SQLiteRekordsEditor(
    driver: SQLiteDriver,
    databasePath: String,
    logger: RekordsLogger = RekordsLogger.None,
): RekordsEditor = SQLRekordsEditor(databasePath, driver.asSQLDriver().withLogging(logger))
