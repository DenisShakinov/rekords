package io.github.denisshakinov.rekords.sqlite

import platform.Foundation.NSHomeDirectory

/**
 * The documents directory of the application, the same path
 * `NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)` returns.
 *
 * That call is spelled out rather than made because its arguments are numbers of a width that
 * differs between the 32-bit and the 64-bit Apple targets, which the compiler refuses in code
 * shared by both.
 */
internal actual fun databasesDirectory(): String = "${NSHomeDirectory()}/Documents"
