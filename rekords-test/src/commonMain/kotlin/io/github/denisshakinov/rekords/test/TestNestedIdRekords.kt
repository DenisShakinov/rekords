package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import kotlin.reflect.KClass

/** A platform, told apart by two ids of its own. */
@Rekord(type = "nid_platform")
data class TestPlatformRekord(
    @Field(name = ID, id = true)
    val id: String,
    @Field(name = KIND, id = true)
    val kind: Int,
    @Field(name = TITLE)
    val title: String,
) {
    companion object {
        const val ID = "id"
        const val KIND = "kind"
        const val TITLE = "title"
    }
}

/** A game played on a platform: one session per game and platform, the platform being an id. */
@Rekord(type = "nid_session")
data class TestSessionRekord(
    @Field(name = GAME_ID, id = true)
    val gameId: Long,
    @Field(name = PLATFORM, id = true)
    val platform: TestPlatformRekord,
    @Field(name = NOTE)
    val note: String,
) {
    companion object {
        const val GAME_ID = "game_id"
        const val PLATFORM = "platform"
        const val NOTE = "note"
    }
}

/** A run through a session, keyed by the session - itself keyed by its platform - and a number. */
@Rekord(type = "nid_run")
data class TestRunRekord(
    @Field(name = SESSION, id = true)
    val session: TestSessionRekord,
    @Field(name = NUMBER, id = true)
    val number: Int,
    @Field(name = HOURS)
    val hours: Int,
) {
    companion object {
        const val SESSION = "session"
        const val NUMBER = "number"
        const val HOURS = "hours"
    }
}

/** Sessions listed by a library, whose list links to rekords keyed by a rekord of their own. */
@Rekord(type = "nid_library")
data class TestLibraryRekord(
    @Field(name = ID, id = true)
    val id: Long,
    @Field(name = SESSIONS)
    val sessions: List<TestSessionRekord>,
) {
    companion object {
        const val ID = "id"
        const val SESSIONS = "sessions"
    }
}

class TestNestedIdRekordsSchema : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(
        TestPlatformRekord::class,
        TestSessionRekord::class,
        TestRunRekord::class,
        TestLibraryRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}
