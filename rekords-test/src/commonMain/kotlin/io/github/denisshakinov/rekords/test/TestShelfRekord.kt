package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import kotlin.reflect.KClass

/**
 * A rekord nesting others three levels deep - a shelf, its books, their authors, their countries -
 * and one type in several fields of it: two books of their own and a list of them.
 */
@Rekord(type = "nest_shelf")
data class TestShelfRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = NAME)
    val name: String,
    @Field(name = FEATURED)
    val featured: TestBookRekord?,
    @Field(name = LATEST)
    val latest: TestBookRekord?,
    @Field(name = BOOKS)
    val books: List<TestBookRekord>,
    @Field(name = CURATORS)
    val curators: List<TestAuthorRekord>,
) {
    companion object {
        const val ID = "id"
        const val NAME = "name"
        const val FEATURED = "featured"
        const val LATEST = "latest"
        const val BOOKS = "books"
        const val CURATORS = "curators"
    }
}

@Rekord(type = "nest_book")
data class TestBookRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = TITLE)
    val title: String,
    @Field(name = AUTHOR)
    val author: TestAuthorRekord?,
    @Field(name = COAUTHORS)
    val coauthors: List<TestAuthorRekord>,
) {
    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val AUTHOR = "author"
        const val COAUTHORS = "coauthors"
    }
}

/** Identified by two fields, so that what refers to it is made of both. */
@Rekord(type = "nest_author")
data class TestAuthorRekord(
    @Field(name = ID, id = true)
    val id: Int,
    @Field(name = PEN_NAME, id = true)
    val penName: String,
    @Field(name = COUNTRY)
    val country: TestCountryRekord?,
) {
    companion object {
        const val ID = "id"
        const val PEN_NAME = "pen_name"
        const val COUNTRY = "country"
    }
}

@Rekord(type = "nest_country")
data class TestCountryRekord(
    @Field(name = CODE, id = true)
    val code: String,
    @Field(name = NAME)
    val name: String,
) {
    companion object {
        const val CODE = "code"
        const val NAME = "name"
    }
}

class TestShelfRekordsSchema : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(
        TestShelfRekord::class,
        TestBookRekord::class,
        TestAuthorRekord::class,
        TestCountryRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}
