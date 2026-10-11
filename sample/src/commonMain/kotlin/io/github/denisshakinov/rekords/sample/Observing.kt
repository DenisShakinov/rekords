package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn

/** Rekords read once the flow is collected, and again each time a transaction writing them commits. */
suspend fun observing() = coroutineScope {
    section("Observing")
    val store = notesStore(name = "observing.db")

    val urgentNotes: Flow<List<String>> = store
        .observeRekords<NoteRekord>(
            filter = Filter.Nested(NoteRekord.TAGS, Filter.Equals(TagRekord.ID, urgent.id)),
            orderBy = listOf(Order.Ascending(NoteRekord.ID)),
        )
        .map { notes -> notes.map { note -> "${note.title} ${note.tags.map { it.name }}" } }
    val updates = urgentNotes.produceIn(this)
    show("Urgent", updates.receive())

    store.putRekord(note(6, "Renew the passport", home, due = today, urgent)).getOrThrow()
    show("After adding an urgent note", updates.receive())

    // A tag is a nested rekord: writing it reads the notes holding it again.
    store.putRekord(TagRekord(id = urgent.id, name = "asap")).getOrThrow()
    show("After renaming the tag", updates.receive())

    updates.cancel()
}
