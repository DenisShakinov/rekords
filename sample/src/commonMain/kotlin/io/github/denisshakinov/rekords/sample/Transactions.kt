package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.deleteRekord
import io.github.denisshakinov.rekords.core.putRekord

/** Operations applied together once the transaction completes, or not at all if it throws. */
suspend fun transactions() {
    section("Transactions")
    val store = notesStore(name = "transactions.db")
    val oldNote = notes[1]
    val newNote = note(6, "Post to Slack", work, due = today, urgent)

    store.transaction {
        deleteRekord(oldNote)
        putRekord(newNote)
    }.getOrThrow()
    show("After the transaction", store.count<NoteRekord>().getOrThrow())

    val failed = store.transaction {
        deleteRekord(newNote)
        error("Something went wrong halfway")
    }
    show("Failed transaction", failed.exceptionOrNull()?.message)
    show("Still there, as nothing was applied", store.count<NoteRekord>().getOrThrow())
}
