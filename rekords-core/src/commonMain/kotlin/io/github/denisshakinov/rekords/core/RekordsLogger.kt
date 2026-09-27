package io.github.denisshakinov.rekords.core

/** Severity of a message reported to a [RekordsLogger]. */
enum class RekordsLogLevel {
    Debug,
    Info,
    Warning,
    Error,
}

/**
 * Receives diagnostics from a rekords implementation, such as the statements a SQL editor runs.
 *
 * Implementations are expected to be cheap: a logger is called on the thread performing the rekords
 * operation and is free to drop messages it is not interested in.
 */
fun interface RekordsLogger {

    fun log(level: RekordsLogLevel, tag: String?, message: String, throwable: Throwable?)

    companion object {

        /** A logger that discards everything, used when no logging is requested. */
        val None: RekordsLogger = RekordsLogger { _, _, _, _ -> }
    }
}

fun RekordsLogger.debug(tag: String? = null, message: String, throwable: Throwable? = null) =
    log(RekordsLogLevel.Debug, tag, message, throwable)

fun RekordsLogger.info(tag: String? = null, message: String, throwable: Throwable? = null) =
    log(RekordsLogLevel.Info, tag, message, throwable)

fun RekordsLogger.warning(tag: String? = null, message: String, throwable: Throwable? = null) =
    log(RekordsLogLevel.Warning, tag, message, throwable)

fun RekordsLogger.error(tag: String? = null, message: String, throwable: Throwable? = null) =
    log(RekordsLogLevel.Error, tag, message, throwable)
