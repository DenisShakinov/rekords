package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.delete
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** The day the samples take for today, so that every run prints the same. */
val today = LocalDate(2026, 10, 8)

val alice = UserRekord(id = 1, name = "Alice")
val bob = UserRekord(id = 2, name = "Bob")

val work = FolderRekord(id = 1, name = "Work", owner = alice)
val home = FolderRekord(id = 2, name = "Home", owner = bob)

val urgent = TagRekord(id = 1, name = "urgent")
val draft = TagRekord(id = 2, name = "draft")
val ideas = TagRekord(id = 3, name = "ideas")

val notes = listOf(
    note(1, "Release 0.1.1", work, due = LocalDate(2026, 10, 9), urgent, pinned = true),
    note(2, "Draft the Slack post", work, due = LocalDate(2026, 10, 10), draft),
    note(3, "Buy groceries", home, due = today, urgent),
    note(4, "Draft a talk on storage", work, due = null, draft, ideas),
    note(5, "Plan the holidays", home, due = LocalDate(2026, 12, 20), ideas),
)

fun note(
    id: Long,
    title: String,
    folder: FolderRekord,
    due: LocalDate?,
    vararg tags: TagRekord,
    pinned: Boolean = false,
) = NoteRekord(
    id = id,
    title = title,
    created = LocalDateTime(2026, 10, 1, 9, id.toInt()),
    dueDate = due,
    folder = folder,
    tags = tags.toList(),
    pinned = pinned,
)

/** A store of [NotesSchema] in the storage [name] names, holding [notes] and nothing else. */
suspend fun notesStore(name: String): RekordsStore {
    val store = RekordsStore(NotesSchema()) {
        this.name = name
    }
    store.delete<NoteRekord>().getOrThrow()
    store.putRekords(notes).getOrThrow()
    return store
}

fun section(title: String) = println("\n=== $title ===")

fun show(label: String, value: Any?) = when (value) {
    is List<*> -> {
        println("$label:")
        value.forEach { println("  $it") }
    }
    else -> println("$label: $value")
}
