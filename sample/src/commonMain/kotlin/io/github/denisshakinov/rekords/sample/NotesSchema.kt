package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import io.github.denisshakinov.rekords.core.addField
import io.github.denisshakinov.rekords.core.removeField
import io.github.denisshakinov.rekords.core.renameField
import kotlin.reflect.KClass

/**
 * The application's schema, and the only one it keeps: the rekord classes as they are now, at the
 * latest [version].
 *
 * A new storage is created straight at this version, with every rekord type listed, and
 * [onUpgrade] is not run. A storage written by any earlier release is handed to [onUpgrade], which
 * takes it through every version after its own in turn - so the classes and schemas of past
 * releases are never kept, only the steps between them.
 */
class NotesSchema : RekordsSchema {

    override val version: Int = 3

    override val rekordTypes: List<KClass<*>> = listOf(
        NoteRekord::class,
        FolderRekord::class,
        UserRekord::class,
        TagRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) {
        // Version 2: notes got a due date, and lost their color.
        if (oldVersion < 2) {
            // No value in the notes stored before, as the field can be null.
            addField<NoteRekord>(NoteRekord.DUE_DATE)
            // A field the classes no longer declare is named as the storage knows it.
            removeField<NoteRekord>("color")
        }
        // Version 3: the text of a note became its title, and notes can be pinned.
        if (oldVersion < 3) {
            renameField<NoteRekord>("text", NoteRekord.TITLE)
            // The value the notes stored before are given.
            addField<NoteRekord>(NoteRekord.PINNED, defaultValue = false)
        }
    }
}
