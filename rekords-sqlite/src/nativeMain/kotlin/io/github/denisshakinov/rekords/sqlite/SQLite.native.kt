@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.sqlite

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.RekordsEngines

internal actual fun platformDriver(): SQLiteDriver = NativeSQLiteDriver()

internal actual fun databasePath(name: String): String = "${databasesDirectory()}/$name"

/** The directory this platform keeps application databases in. */
internal expect fun databasesDirectory(): String

// Registers the engine as the binary starts: there is no ServiceLoader to find it here. The
// annotation is deprecated, and kept by the stdlib for as long as nothing replaces it - Ktor
// registers its engines the same way.
@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val registration: Unit = RekordsEngines.register(SQLite)
