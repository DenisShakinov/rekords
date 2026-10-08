package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/** Writing, reading, counting and deleting rekords. Every operation returns a `Result`. */
suspend fun storeOperations() {
    section("Store")
    // Common code creates the store with the engine the target depends on.
    val store = RekordsStore(NotesSchema()) {
        name = "store.db"
    }

    store.putRekords(notes).getOrThrow()
    // Writing a rekord with the same ids updates it instead of adding another.
    store.putRekord(note(3, "Buy groceries and flowers", home, due = today, urgent)).getOrThrow()

    val note: NoteRekord? = store
        .getRekord<NoteRekord>(Filter.Equals(NoteRekord.ID, 3L))
        .getOrThrow()
    show("Note 3", note)

    val dueThisWeek: List<NoteRekord> = store
        .getRekords<NoteRekord>(
            filter = Filter.GreaterThanOrEquals(NoteRekord.DUE_DATE, today) +
                Filter.LessThan(NoteRekord.DUE_DATE, today.plus(1, DateTimeUnit.WEEK)),
            orderBy = listOf(Order.Ascending(NoteRekord.DUE_DATE)),
            limit = 20,
        )
        .getOrThrow()
    show("Due this week", dueThisWeek)

    val secondPage = store
        .getRekords<NoteRekord>(
            orderBy = listOf(Order.Ascending(NoteRekord.ID)),
            limit = 2,
            offset = 2,
        )
        .getOrThrow()
    show("Second page of two", secondPage)

    val drafts: Int = store
        .count<NoteRekord>(Filter.Contains(NoteRekord.TITLE, "Draft"))
        .getOrThrow()
    show("Notes with \"Draft\" in the title", drafts)

    store.deleteRekord(notes.first()).getOrThrow()
    store.delete<NoteRekord>(Filter.LessThan(NoteRekord.CREATED, LocalDateTime(2026, 10, 1, 9, 3)))
        .getOrThrow()
    show("Left after deleting", store.getRekords<NoteRekord>().getOrThrow())
}
