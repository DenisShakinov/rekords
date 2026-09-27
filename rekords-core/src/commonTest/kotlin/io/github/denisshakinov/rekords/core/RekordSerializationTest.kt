@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import io.github.denisshakinov.rekords.test.TestGamePropertyRekord
import io.github.denisshakinov.rekords.test.TestGameRekord
import io.github.denisshakinov.rekords.test.TestGameStateRekord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RekordSerializationTest {
    private companion object {
        val game1 = TestGameRekord(
            id = 3,
            title = "Silent Hill",
            releaseDate = LocalDate(1999, 2, 23),
            finishedDateTime = LocalDateTime(2023, 10, 28, 14, 33),
            remasterDate = LocalDate(2024, 10, 8),
            steamId = null,
            finished = true,
            score = 8.1f,
            metacritic = 86 / 100.0,
            state = TestGameStateRekord(id = 3, name = "Completed"),
            properties = listOf(
                TestGamePropertyRekord(
                    id = "silent_hill",
                    type = TestGamePropertyRekord.TYPE_SERIES,
                    title = "Silent Hill",
                    locale = "en"
                ),
                TestGamePropertyRekord(
                    id = "survival_horror",
                    type = TestGamePropertyRekord.TYPE_GENRE,
                    title = "Survival Horror",
                    locale = "en"
                ),
                TestGamePropertyRekord(
                    id = "playstation",
                    type = TestGamePropertyRekord.TYPE_PLATFORM,
                    title = "PlayStation",
                    locale = "en"
                ),
                TestGamePropertyRekord(
                    id = "team_silent",
                    type = TestGamePropertyRekord.TYPE_DEVELOPER,
                    title = "Team Silent",
                    locale = "en"
                ),
                TestGamePropertyRekord(
                    id = "konami",
                    type = TestGamePropertyRekord.TYPE_PUBLISHER,
                    title = "Konami",
                    locale = "en"
                ),
            ),
        )
    }

    @Test
    fun encodeToMapTest() {
        val values: RekordValues = encode(game1)
        assertEquals(expected = 11, actual = values.size)
        assertEquals(
            expected = game1.id,
            actual = (values[TestGameRekord.ID] as RekordValue.Primitive).value
        )
        assertEquals(
            expected = game1.title,
            actual = (values[TestGameRekord.TITLE] as RekordValue.Primitive).value
        )
        assertEquals(
            expected = game1.releaseDate.encode(),
            actual = (values[TestGameRekord.RELEASE_DATE] as RekordValue.Primitive).value
        )
        assertEquals(
            expected = game1.finishedDateTime.encode(),
            actual = (values[TestGameRekord.FINISHED_DATE_TIME] as RekordValue.Primitive).value
        )
        // A nullable date encodes exactly like a non-nullable one, or it would be stored in a
        // shape no filter on it can match.
        assertEquals(
            expected = game1.remasterDate.encode(),
            actual = (values[TestGameRekord.REMASTER_DATE] as RekordValue.Primitive).value
        )
        assertEquals(expected = null, actual = values[TestGameRekord.STEAM_ID])
        assertEquals(
            expected = game1.finished,
            actual = (values[TestGameRekord.FINISHED] as RekordValue.Primitive).value
        )
        assertEquals(
            expected = game1.score,
            actual = (values[TestGameRekord.SCORE] as RekordValue.Primitive).value
        )
        assertEquals(
            expected = game1.metacritic,
            actual = (values[TestGameRekord.METACRITIC] as RekordValue.Primitive).value
        )
        assertIs<RekordValue.Composite>(values[TestGameRekord.STATE])
        assertIs<RekordValue.CompositeList>(values[TestGameRekord.PROPERTIES])
        assertEquals(
            expected = 5,
            actual = (values[TestGameRekord.PROPERTIES] as RekordValue.CompositeList).list.size
        )
    }

    @Test
    fun decodeFromMapTest() {
        val values: RekordValues = encode(game1)
        val decodedGame: TestGameRekord = values.decode()
        assertEquals(expected = game1.id, actual = decodedGame.id)
        assertEquals(expected = game1.title, actual = decodedGame.title)
        assertEquals(expected = game1.releaseDate, actual = decodedGame.releaseDate)
        assertEquals(expected = game1.finishedDateTime, actual = decodedGame.finishedDateTime)
        assertEquals(expected = game1.steamId, actual = decodedGame.steamId)
        assertEquals(expected = game1.finished, actual = decodedGame.finished)
        assertEquals(expected = game1.score, actual = decodedGame.score)
        assertEquals(expected = game1.metacritic, actual = decodedGame.metacritic)

        // state
        assertEquals(expected = game1.state.id, actual = decodedGame.state.id)
        assertEquals(expected = game1.state.name, actual = decodedGame.state.name)

        // properties
        assertEquals(expected = game1.properties.size, actual = decodedGame.properties.size)
        game1.properties.forEachIndexed { index, expected ->
            val actual = decodedGame.properties[index]
            assertEquals(expected = expected.id, actual = actual.id)
            assertEquals(expected = expected.type, actual = actual.type)
            assertEquals(expected = expected.title, actual = actual.title)
            assertEquals(expected = expected.locale, actual = actual.locale)
        }
    }

    @Test
    fun idFieldsTest() {
        val idFields: List<String> = idFields<TestGameRekord>()
        assertEquals(expected = 2, actual = idFields.size)
        assertEquals(expected = TestGameRekord.ID, actual = idFields[0])
        assertEquals(expected = TestGameRekord.TITLE, actual = idFields[1])
    }

    @Test
    fun rekordTypeTest() {
        val rekordType: String = rekordType<TestGameRekord>()
        assertEquals(expected = TestGameRekord.REKORD_TYPE, actual = rekordType)
    }
}