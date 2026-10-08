package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.delete
import io.github.denisshakinov.rekords.core.putRekords
import io.github.denisshakinov.rekords.memory.InMemory

/**
 * An in-memory store as the application's cache in front of a persistent one: rekords are read and
 * written in memory, written to the storage from time to time and read back from it at start.
 */
suspend fun caching() {
    section("Caching in memory")
    val storage = notesStore(name = "cache.db")
    val byId = listOf(Order.Ascending(NoteRekord.ID))

    // At start: fill the cache from the storage.
    val cache = RekordsStore(NotesSchema(), InMemory)
    cache.putRekords(storage.getRekords<NoteRekord>().getOrThrow()).getOrThrow()

    // The application works with the cache alone.
    cache.putRekord(note(6, "Cached, not stored yet", home, due = today, urgent)).getOrThrow()
    cache.delete<NoteRekord>(Filter.Equals(NoteRekord.ID, 5L)).getOrThrow()
    show("Notes in the cache", cache.count<NoteRekord>().getOrThrow())
    show("Notes in the storage, not written yet", storage.count<NoteRekord>().getOrThrow())

    // From time to time: replace what the storage holds with what the cache does, in one
    // transaction. A transaction runs nothing but its own store's operations, so the cache is read
    // before it.
    val cached = cache.getRekords<NoteRekord>().getOrThrow()
    storage.transaction {
        delete<NoteRekord>()
        putRekords(cached)
    }.getOrThrow()
    val stored = storage.getRekords<NoteRekord>(orderBy = byId).getOrThrow()
    show("The storage after writing the cache", stored)

    // As the next start does: a new cache filled from the storage.
    val nextCache = RekordsStore(NotesSchema(), InMemory)
    nextCache.putRekords(storage.getRekords<NoteRekord>().getOrThrow()).getOrThrow()
    show("A new cache filled from the storage", nextCache.count<NoteRekord>().getOrThrow())
}
