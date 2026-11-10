package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Encryption
import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import kotlin.reflect.KClass

/** A writer given a number as it is first put. */
@Rekord(type = "gid_writer")
data class TestWriterRekord(
    @Field(name = ID, id = true, generated = true)
    val id: Long = 0,
    @Field(name = NAME)
    val name: String,
) {
    companion object {
        const val ID = "id"
        const val NAME = "name"
    }
}

/** A section given an Int number as it is first put. */
@Rekord(type = "gid_section")
data class TestSectionRekord(
    @Field(name = ID, id = true, generated = true)
    val id: Int = 0,
    @Field(name = TITLE)
    val title: String,
) {
    companion object {
        const val ID = "id"
        const val TITLE = "title"
    }
}

/** A novel given a UUID as it is first put, holding a writer and sections given ids of their own. */
@Rekord(type = "gid_novel")
data class TestNovelRekord(
    @Field(name = ID, id = true, generated = true)
    val id: String = "",
    @Field(name = TITLE)
    val title: String,
    @Field(name = WRITER)
    val writer: TestWriterRekord,
    @Field(name = SECTIONS)
    val sections: List<TestSectionRekord>,
) {
    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val WRITER = "writer"
        const val SECTIONS = "sections"
    }
}

/** A memo whose UUID is encrypted, deterministically, so that it is still looked up by it. */
@Rekord(type = "gid_memo")
data class TestMemoRekord(
    @Field(name = ID, id = true, generated = true, encryption = Encryption.Deterministic)
    val id: String = "",
    @Field(name = TEXT)
    val text: String,
) {
    companion object {
        const val ID = "id"
        const val TEXT = "text"
    }
}

class TestGeneratedIdRekordsSchema : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(
        TestWriterRekord::class,
        TestSectionRekord::class,
        TestNovelRekord::class,
        TestMemoRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}
