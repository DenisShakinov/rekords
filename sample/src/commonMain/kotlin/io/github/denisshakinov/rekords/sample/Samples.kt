package io.github.denisshakinov.rekords.sample

/** Runs every sample in turn, each on a storage of its own. */
suspend fun runSamples() {
    storeOperations()
    filters()
    transactions()
    migration()
    encryption()
    engines()
    caching()
}
