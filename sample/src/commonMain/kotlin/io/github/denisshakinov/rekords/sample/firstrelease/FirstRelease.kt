package io.github.denisshakinov.rekords.sample.firstrelease

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import io.github.denisshakinov.rekords.sample.FolderRekord
import io.github.denisshakinov.rekords.sample.NotesSchema
import io.github.denisshakinov.rekords.sample.TagRekord
import io.github.denisshakinov.rekords.sample.UserRekord
import kotlinx.datetime.LocalDateTime
import kotlin.reflect.KClass

/*
 * Stands in for the application as its first release shipped it, so that the migration sample has
 * a storage of version 1 to upgrade - the one a user installing that release would have.
 *
 * An application has no such code: it keeps nothing but its latest schema, NotesSchema, whose
 * onUpgrade takes a storage of any earlier version to the latest.
 */

/** The note of version 1: no due date and no pin, its title called "text", and a color. */
@Rekord(type = "note")
class FirstReleaseNoteRekord(
    @Field(name = "id", id = true)
    val id: Long,
    @Field(name = "text", searchable = true)
    val text: String,
    @Field(name = "created")
    val created: LocalDateTime,
    @Field(name = "color")
    val color: String,
    @Field(name = "folder")
    val folder: FolderRekord,
    @Field(name = "tags")
    val tags: List<TagRekord>,
)

/** [NotesSchema] at version 1. */
class FirstReleaseSchema : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(
        FirstReleaseNoteRekord::class,
        FolderRekord::class,
        UserRekord::class,
        TagRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}
