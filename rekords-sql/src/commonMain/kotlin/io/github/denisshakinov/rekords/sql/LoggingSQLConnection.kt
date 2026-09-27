package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.PrimitiveRekordValues
import io.github.denisshakinov.rekords.core.RekordsLogger
import io.github.denisshakinov.rekords.core.debug

private const val TAG = "SQLConnection"

private class LoggingSQLConnection(
    private val connection: SQLConnection,
    private val logger: RekordsLogger,
) : SQLConnection by connection {

    override suspend fun rawQuery(
        query: String,
        args: Iterable<PrimitiveRekordValue?>?
    ): Iterable<PrimitiveRekordValues> {
        logger.debug(TAG, "query: $query, args: $args")
        return connection.rawQuery(query, args)
    }
}

fun SQLDriver.withLogging(logger: RekordsLogger): SQLDriver = object : SQLDriver {
    override suspend fun open(databasePath: String): SQLConnection =
        LoggingSQLConnection(this@withLogging.open(databasePath), logger)
}
