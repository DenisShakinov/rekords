package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.test.TestGameRekord.Companion.REKORD_TYPE
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@Rekord(type = REKORD_TYPE)
class TestGameRekord(
    @Field(name = ID, id = true)
    val id: Int,
    @Field(name = TITLE, id = true)
    val title: String,
    @Field(name = RELEASE_DATE)
    val releaseDate: LocalDate,
    @Field(name = FINISHED_DATE_TIME)
    val finishedDateTime: LocalDateTime = LocalDateTime(2023, 10, 28, 14, 33),
    /** Nullable on purpose: a nullable date is stored and filtered differently if mishandled. */
    @Field(name = REMASTER_DATE)
    val remasterDate: LocalDate? = null,
    @Field(name = STEAM_ID)
    val steamId: Long? = null,
    @Field(name = FINISHED)
    val finished: Boolean = true,
    @Field(name = SCORE)
    val score: Float = 8.1f,
    @Field(name = METACRITIC)
    val metacritic: Double = 86 / 100.0,
    @Field(name = STATE)
    val state: TestGameStateRekord,
    @Field(name = PROPERTIES)
    val properties: List<TestGamePropertyRekord>,
) {
    companion object {
        const val REKORD_TYPE = "game"
        const val ID = "id"
        const val TITLE = "title"
        const val RELEASE_DATE = "release_date"
        const val FINISHED_DATE_TIME = "finished_date_time"
        const val REMASTER_DATE = "remaster_date"
        const val STEAM_ID = "steam_id"
        const val FINISHED = "finished"
        const val SCORE = "score"
        const val METACRITIC = "metacritic"
        const val STATE = "state"
        const val PROPERTIES = "properties"
    }
}