package io.github.denisshakinov.rekords.sql

interface SQLDriver {

    /**
     * Opens a connection to the database at [databasePath].
     *
     * Suspending because a driver is free to do its work somewhere other than the calling thread -
     * a web worker, for instance.
     */
    suspend fun open(databasePath: String): SQLConnection
}
