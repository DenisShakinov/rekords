package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * A note, holding a rekord - its folder - and a list of them - its tags. The nested rekords are
 * stored once and shared between the notes referring to them.
 */
@Rekord(type = "note")
class NoteRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = TITLE, searchable = true)
    val title: String,
    @Field(name = CREATED)
    val created: LocalDateTime,
    @Field(name = DUE_DATE)
    val dueDate: LocalDate?,
    @Field(name = FOLDER)
    val folder: FolderRekord,
    @Field(name = TAGS)
    val tags: List<TagRekord>,
    @Field(name = PINNED)
    val pinned: Boolean,
) {
    override fun toString() = "#$id \"$title\" in ${folder.name}" +
        (dueDate?.let { ", due $it" } ?: "") +
        (if (pinned) ", pinned" else "") +
        (if (tags.isEmpty()) "" else tags.joinToString(prefix = " [", postfix = "]") { it.name })

    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val CREATED = "created"
        const val DUE_DATE = "due_date"
        const val FOLDER = "folder"
        const val TAGS = "tags"
        const val PINNED = "pinned"
    }
}

@Rekord(type = "folder")
class FolderRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = NAME)
    val name: String,
    @Field(name = OWNER)
    val owner: UserRekord,
) {
    companion object {
        const val ID = "id"
        const val NAME = "name"
        const val OWNER = "owner"
    }
}

@Rekord(type = "user")
class UserRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = NAME)
    val name: String,
) {
    companion object {
        const val ID = "id"
        const val NAME = "name"
    }
}

@Rekord(type = "tag")
class TagRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = NAME, searchable = true)
    val name: String,
) {
    companion object {
        const val ID = "id"
        const val NAME = "name"
    }
}
