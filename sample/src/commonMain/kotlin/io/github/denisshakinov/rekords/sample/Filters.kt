package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.or
import io.github.denisshakinov.rekords.core.plus

/** Combining filters, and filtering by the fields of nested rekords. */
suspend fun filters() {
    section("Filters")
    val store = notesStore(name = "filters.db")
    val byId = listOf(Order.Ascending(NoteRekord.ID))

    // `+` is "and", `or` is "or".
    val workDrafts = Filter.Nested(NoteRekord.FOLDER, Filter.Equals(FolderRekord.NAME, "Work")) +
        Filter.Contains(NoteRekord.TITLE, "Draft")
    show("Drafts at work", store.getRekords<NoteRekord>(workDrafts, byId).getOrThrow())

    val dueTodayOrUndated = Filter.Equals(NoteRekord.DUE_DATE, today) or
        Filter.Equals(NoteRekord.DUE_DATE, null)
    show("Due today or undated", store.getRekords<NoteRekord>(dueTodayOrUndated, byId).getOrThrow())

    val notInFirstThree = Filter.Not(Filter.InList(NoteRekord.ID, listOf(1L, 2L, 3L)))
    show(
        "Not among the first three",
        store.getRekords<NoteRekord>(notInFirstThree, byId).getOrThrow(),
    )

    // A rekord is selected once however many of the rekords in its list match.
    val taggedIdeasOrDraft = Filter.Or(
        Filter.Nested(NoteRekord.TAGS, Filter.Equals(TagRekord.NAME, "ideas")),
        Filter.Nested(NoteRekord.TAGS, Filter.Equals(TagRekord.NAME, "draft")),
    )
    show(
        "Tagged ideas or draft",
        store.getRekords<NoteRekord>(taggedIdeasOrDraft, byId).getOrThrow(),
    )

    // Nested filters look as deep as rekords are nested.
    val ownedByBob = Filter.Nested(
        NoteRekord.FOLDER,
        Filter.Nested(FolderRekord.OWNER, Filter.Equals(UserRekord.ID, bob.id)),
    )
    show("In Bob's folders", store.getRekords<NoteRekord>(ownedByBob, byId).getOrThrow())
}
