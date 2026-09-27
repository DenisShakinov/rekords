package io.github.denisshakinov.rekords.core

/** No `ServiceLoader` here: an engine registers itself with [RekordsEngines] as it is loaded. */
internal actual fun serviceEngines(): List<RekordsEngine<RekordsEngineConfig>> = emptyList()
