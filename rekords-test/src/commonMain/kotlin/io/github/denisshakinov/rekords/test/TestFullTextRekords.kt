package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import kotlin.reflect.KClass

/** A tag, found by the words of its name - within the articles holding it, too. */
@Rekord(type = "fts_tag")
data class TestArticleTagRekord(
    @Field(name = ID, id = true)
    val id: String,
    @Field(name = NAME, fullText = true)
    val name: String,
) {
    companion object {
        const val ID = "id"
        const val NAME = "name"
    }
}

/** An article found by the words of its title and body, but not of its author. */
@Rekord(type = "fts_article")
data class TestArticleRekord(
    @Field(name = ID, id = true)
    val id: Int,
    @Field(name = TITLE, fullText = true)
    val title: String,
    @Field(name = BODY, fullText = true)
    val body: String?,
    @Field(name = AUTHOR)
    val author: String,
    @Field(name = TAGS)
    val tags: List<TestArticleTagRekord>,
) {
    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val BODY = "body"
        const val AUTHOR = "author"
        const val TAGS = "tags"
    }
}

/** A note as it was first kept: its title not fullText, and no summary. */
@Rekord(type = "fts_note")
data class TestPlainNoteRekord(
    @Field(name = TestNoteRekord.ID, id = true)
    val id: Int,
    @Field(name = TestNoteRekord.TITLE)
    val title: String,
)

/** The note once its title has been made fullText, and a fullText summary added. */
@Rekord(type = "fts_note")
data class TestNoteRekord(
    @Field(name = ID, id = true)
    val id: Int,
    @Field(name = TITLE, fullText = true)
    val title: String,
    @Field(name = SUMMARY, fullText = true)
    val summary: String,
) {
    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val SUMMARY = "summary"
    }
}

/**
 * The articles and tags, with the notes as [noteClass] declares them, at [version] - which
 * [upgrade] brings a storage of an older version up to.
 */
class TestFullTextRekordsSchema(
    override val version: Int = 1,
    noteClass: KClass<*> = TestPlainNoteRekord::class,
    private val upgrade: suspend RekordsMigrationEditor.() -> Unit = {},
) : RekordsSchema {

    override val rekordTypes: List<KClass<*>> = listOf(
        TestArticleRekord::class,
        TestArticleTagRekord::class,
        noteClass,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = upgrade()
}
