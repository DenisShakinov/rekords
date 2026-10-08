package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.RekordsLogger
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.memory.InMemory

/** The engine a store keeps its rekords with: found on its own, or named. */
suspend fun engines() {
    section("Engines")
    // Named: the in-memory engine does not register itself, so a store has to be given it.
    val inMemory = RekordsStore(NotesSchema(), InMemory)
    inMemory.putRekords(notes).getOrThrow()
    show("Notes in memory", inMemory.count<NoteRekord>().getOrThrow())

    // Found on its own - SQLite on the JVM - and followed with a logger, which is handed the
    // statements the SQLite engine runs.
    val logged = RekordsStore(NotesSchema()) {
        name = "engines.db"
        logger = RekordsLogger { level, tag, message, throwable ->
            println("  [$level] $tag: ${message.lines().first().take(100)}")
            throwable?.printStackTrace()
        }
    }
    println("Counting notes in the storage:")
    show("Notes in the storage", logged.count<NoteRekord>().getOrThrow())
}
