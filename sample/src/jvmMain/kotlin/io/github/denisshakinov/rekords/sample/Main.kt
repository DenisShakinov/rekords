package io.github.denisshakinov.rekords.sample

import kotlinx.coroutines.runBlocking
import java.io.File

fun main() = runBlocking {
    // The migration sample upgrades a storage the first release has just written, which one a
    // previous run left - already upgraded - is not. `./gradlew :sample:run` starts in an empty
    // directory; a run from the IDE starts wherever the last one left its databases.
    listOf("", "-journal", "-wal", "-shm").forEach { File("migration.db$it").delete() }
    runSamples()
}
