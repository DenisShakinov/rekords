package io.github.denisshakinov.rekords.indexeddb

// Registers the engine as the module is loaded: there is no ServiceLoader to find it on the web.
// The annotation is deprecated, and kept by the stdlib for as long as nothing replaces it - Ktor
// registers its engines the same way.
@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val registration: Unit = registerIndexedDB()
