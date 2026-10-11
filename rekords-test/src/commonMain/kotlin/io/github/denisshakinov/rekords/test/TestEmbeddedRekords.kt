package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Embedded
import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import kotlinx.datetime.LocalDate
import kotlin.reflect.KClass

/** The two fields a game is told apart by, kept within each rekord referring to a game. */
@Embedded
data class TestGameKey(
    @Field(name = ID)
    val id: String,
    @Field(name = ID_TYPE)
    val idType: String,
) {
    companion object {
        const val ID = "id"
        const val ID_TYPE = "id_type"
    }
}

/** When a game came out, and on what - a rekord held within an embedded class. */
@Embedded
data class TestRelease(
    @Field(name = DATE)
    val date: LocalDate,
    @Field(name = PLATFORM)
    val platform: TestPlatformRekord?,
) {
    companion object {
        const val DATE = "date"
        const val PLATFORM = "platform"
    }
}

/** A game keyed by its key under the key's own names, `id` and `id_type`, and the platforms it was ported to. */
@Rekord(type = "emb_game")
data class TestKeyedGameRekord(
    @Field(name = KEY, id = true, prefixed = false)
    val key: TestGameKey,
    @Field(name = TITLE)
    val title: String,
    @Field(name = RELEASE)
    val release: TestRelease?,
    // A list, linked to the game by the fields of its key.
    @Field(name = PORTS)
    val ports: List<TestPlatformRekord>,
) {
    companion object {
        const val KEY = "key"
        const val TITLE = "title"
        const val RELEASE = "release"
        const val PORTS = "ports"
    }
}

/** A game played on a platform, keyed by the game's key under its name - `game_id`, `game_id_type`. */
@Rekord(type = "emb_play")
data class TestPlayRekord(
    @Field(name = GAME, id = true)
    val game: TestGameKey,
    @Field(name = PLATFORM, id = true)
    val platform: TestPlatformRekord,
    @Field(name = HOURS)
    val hours: Int,
) {
    companion object {
        const val GAME = "game"
        const val PLATFORM = "platform"
        const val HOURS = "hours"
    }
}

class TestEmbeddedRekordsSchema : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(
        TestKeyedGameRekord::class,
        TestPlayRekord::class,
        TestPlatformRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}
