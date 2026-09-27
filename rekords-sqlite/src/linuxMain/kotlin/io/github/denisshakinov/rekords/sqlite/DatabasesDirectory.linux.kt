@file:OptIn(ExperimentalForeignApi::class)

package io.github.denisshakinov.rekords.sqlite

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

internal actual fun databasesDirectory(): String =
    environmentPath("XDG_DATA_HOME")
        ?: environmentPath("HOME")?.let { home -> "$home/.local/share" }
        ?: "."

private fun environmentPath(name: String): String? =
    getenv(name)?.toKString()?.takeIf { it.isNotEmpty() }
