package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.sample.firstrelease.FirstReleaseNoteRekord
import io.github.denisshakinov.rekords.sample.firstrelease.FirstReleaseSchema
import kotlinx.datetime.LocalDateTime

/**
 * A storage the first release wrote, opened by the application as it is now: the first operation
 * runs [NotesSchema.onUpgrade] from version 1 to 3, in a transaction of its own.
 */
suspend fun migration() {
    section("Migration")
    writtenByTheFirstRelease(name = "migration.db")

    // The application opens it with its one schema, as it opens any storage.
    val store = RekordsStore(NotesSchema()) {
        name = "migration.db"
    }
    // "text" is now the title, the due date is null, the pin is false and the color is gone.
    val upgraded = store
        .getRekords<NoteRekord>(orderBy = listOf(Order.Ascending(NoteRekord.ID)))
        .getOrThrow()
    show(
        "Read at version 3",
        upgraded.map {
            "#${it.id} title \"${it.title}\", due date ${it.dueDate}, pinned ${it.pinned}"
        },
    )
    show("Fields stored", store.query<NoteRekord>(limit = 1).getOrThrow().single().keys)
}

/** What a user of the first release has on their device - see [FirstReleaseSchema]. */
private suspend fun writtenByTheFirstRelease(name: String) {
    val store = RekordsStore(FirstReleaseSchema()) {
        this.name = name
    }
    val written = listOf(
        FirstReleaseNoteRekord(
            id = 1,
            text = "Written by the first release",
            created = firstReleaseDay,
            color = "yellow",
            folder = work,
            tags = listOf(ideas),
        ),
        FirstReleaseNoteRekord(
            id = 2,
            text = "Me too",
            created = firstReleaseDay,
            color = "blue",
            folder = home,
            tags = emptyList(),
        ),
    )
    store.putRekords(written).getOrThrow()
    show("Written at version 1", written.map { "#${it.id} text \"${it.text}\", color ${it.color}" })
    store.close()
}

private val firstReleaseDay = LocalDateTime(2026, 1, 15, 12, 0)
