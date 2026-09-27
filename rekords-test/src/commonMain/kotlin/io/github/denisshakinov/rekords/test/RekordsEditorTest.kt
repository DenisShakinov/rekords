@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.Order
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.asRekordValue
import io.github.denisshakinov.rekords.core.count
import io.github.denisshakinov.rekords.core.delete
import io.github.denisshakinov.rekords.core.getRekord
import io.github.denisshakinov.rekords.core.or
import io.github.denisshakinov.rekords.core.plus
import io.github.denisshakinov.rekords.core.put
import io.github.denisshakinov.rekords.core.putRekord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class RekordsEditorTest(private val rekordsEditor: RekordsEditor) {

    private val rekordsSchema: RekordsSchema = TestRekordsSchema()
    private val rekordsStore = RekordsStore(rekordsSchema, rekordsEditor)

    private suspend fun seed() {
        rekordsStore.delete<TestGameRekord>()
        rekordsStore.putRekords(
            listOf(
                TestGameRekord(
                    id = 1,
                    title = "Doom",
                    releaseDate = LocalDate(1993, 12, 10),
                    remasterDate = LocalDate(2019, 7, 26),
                    state = TestGameStateRekord(id = 3, name = "Completed"),
                    properties = listOf(
                        TestGamePropertyRekord(
                            id = "fps",
                            type = TestGamePropertyRekord.TYPE_GENRE,
                            title = "FPS",
                            locale = "en"
                        ),
                        TestGamePropertyRekord(
                            id = "pc",
                            type = TestGamePropertyRekord.TYPE_PLATFORM,
                            title = "PC",
                            locale = "en"
                        ),
                        TestGamePropertyRekord(
                            id = "id_software",
                            type = TestGamePropertyRekord.TYPE_DEVELOPER,
                            title = "id Software",
                            locale = "en"
                        ),
                        TestGamePropertyRekord(
                            id = "gt_interactive",
                            type = TestGamePropertyRekord.TYPE_PUBLISHER,
                            title = "GT Interactive",
                            locale = "en"
                        ),
                    ),
                ),
                TestGameRekord(
                    id = 2,
                    title = "Doom 2",
                    releaseDate = LocalDate(1994, 10, 10),
                    state = TestGameStateRekord(id = 3, name = "Completed"),
                    properties = listOf(
                        TestGamePropertyRekord(
                            id = "doom",
                            type = TestGamePropertyRekord.TYPE_SERIES,
                            title = "Doom",
                            locale = "en"
                        ),
                        TestGamePropertyRekord(
                            id = "fps",
                            type = TestGamePropertyRekord.TYPE_GENRE,
                            title = "FPS",
                            locale = "en"
                        ),
                        TestGamePropertyRekord(
                            id = "pc",
                            type = TestGamePropertyRekord.TYPE_PLATFORM,
                            title = "PC",
                            locale = "en"
                        ),
                        TestGamePropertyRekord(
                            id = "id_software",
                            type = TestGamePropertyRekord.TYPE_DEVELOPER,
                            title = "id Software",
                            locale = "en"
                        ),
                        TestGamePropertyRekord(
                            id = "gt_interactive",
                            type = TestGamePropertyRekord.TYPE_PUBLISHER,
                            title = "GT Interactive",
                            locale = "en"
                        ),
                    ),
                ),
                TestGameRekord(
                    id = 3,
                    title = "Silent Hill",
                    releaseDate = LocalDate(1999, 2, 24),
                    remasterDate = LocalDate(2024, 10, 8),
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
                ),
                TestGameRekord(
                    id = 4,
                    title = "Silent Hill 2",
                    releaseDate = LocalDate(2001, 9, 25),
                    state = TestGameStateRekord(id = 2, name = "Playing"),
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
                            id = "playstation_2",
                            type = TestGamePropertyRekord.TYPE_PLATFORM,
                            title = "PlayStation 2",
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
                ),
                TestGameRekord(
                    id = 5,
                    title = "Silent Hill 3",
                    releaseDate = LocalDate(2003, 5, 23),
                    state = TestGameStateRekord(id = 1, name = "Wishlist"),
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
                            id = "playstation_2",
                            type = TestGamePropertyRekord.TYPE_PLATFORM,
                            title = "PlayStation 2",
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
                ),
            )
        )
    }

    /**
     * Seeds the store and runs [block] in the same coroutine.
     *
     * The seeding cannot live in a `@BeforeTest` function: on Kotlin/JS and Kotlin/Wasm the test
     * framework only awaits the [kotlinx.coroutines.test.TestResult] a `@Test` returns, so a
     * suspending set-up would still be running when the test body starts.
     */
    private fun test(block: suspend () -> Unit) = runTest {
        seed()
        block()
    }

    open fun check_RekordsStore_put_method() = test {
        var game1: TestGameRekord? =
            rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
                .getOrNull()
        // title should be "Doom" at the beginning
        assertEquals(expected = "Doom", actual = game1?.title)
        // update existing game rekord with "Doom (updated)" title where gameId == 1
        rekordsStore.put<TestGameRekord>(
            values = mapOf(TestGameRekord.TITLE to "Doom (updated)".asRekordValue()),
            filter = Filter.Equals(TestGameRekord.ID, 1),
        )
        game1 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
            .getOrNull()
        // title should be "Doom (updated)" after update
        assertEquals(expected = "Doom (updated)", actual = game1?.title)
    }

    /**
     * A nullable date has to be stored the same way a filter encodes it. Getting that wrong keeps
     * every read by id working - the value round-trips - while every range query silently matches
     * nothing.
     */
    open fun check_RekordsStore_query_method_with_nullable_date_filter() = test {
        val remasteredGames: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                filter = Filter.GreaterThanOrEquals(
                    TestGameRekord.REMASTER_DATE,
                    LocalDate(2020, 1, 1),
                ),
            )
            .getOrNull().orEmpty()
        assertEquals(expected = 1, actual = remasteredGames.size)
        assertEquals(expected = "Silent Hill", actual = remasteredGames[0].title)
        assertEquals(expected = LocalDate(2024, 10, 8), actual = remasteredGames[0].remasterDate)

        val orderedGames: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                orderBy = listOf(Order.Descending(TestGameRekord.REMASTER_DATE)),
                limit = 2,
            )
            .getOrNull().orEmpty()
        assertEquals(expected = "Silent Hill", actual = orderedGames[0].title)
        assertEquals(expected = "Doom", actual = orderedGames[1].title)
    }

    open fun check_RekordsStore_query_method_with_filter() = test {
        // filters all games which title contains "Silent Hill"
        val games1: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(Filter.Contains(TestGameRekord.TITLE, "Silent Hill"))
            .getOrNull().orEmpty()
        assertEquals(expected = 3, actual = games1.size)
        assertEquals(expected = "Silent Hill", actual = games1[0].title)
        assertEquals(expected = "Silent Hill 2", actual = games1[1].title)
        assertEquals(expected = "Silent Hill 3", actual = games1[2].title)
        // filters all games which title equals "Silent Hill"
        val games2: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(Filter.Equals(TestGameRekord.TITLE, "Silent Hill"))
            .getOrNull().orEmpty()
        assertEquals(expected = 1, actual = games2.size)
        assertEquals(expected = "Silent Hill", actual = games2[0].title)
        // filters all games which title contains "Silent Hill" and release date after 2000
        val games3: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                filter = Filter.Contains(TestGameRekord.TITLE, "Silent Hill")
                        + Filter.GreaterThan(TestGameRekord.RELEASE_DATE, LocalDate(2000, 1, 1))
            )
            .getOrNull().orEmpty()
        assertEquals(expected = 2, actual = games3.size)
        assertEquals(expected = "Silent Hill 2", actual = games3[0].title)
        assertEquals(expected = "Silent Hill 3", actual = games3[1].title)
        // filters all games which gameId is <= 2 or >= 4
        val games4: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                filter = Filter.LessThanOrEquals(TestGameRekord.ID, 2)
                        or Filter.GreaterThanOrEquals(TestGameRekord.ID, 4)
            )
            .getOrNull().orEmpty()
        assertEquals(expected = 4, actual = games4.size)
        assertEquals(expected = "Doom", actual = games4[0].title)
        assertEquals(expected = "Doom 2", actual = games4[1].title)
        assertEquals(expected = "Silent Hill 2", actual = games4[2].title)
        assertEquals(expected = "Silent Hill 3", actual = games4[3].title)
        // filters all games which gameId is <= 2 or >= 4 and title contains number 2
        val games5: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                filter = (Filter.LessThanOrEquals(TestGameRekord.ID, 2)
                        or Filter.GreaterThanOrEquals(TestGameRekord.ID, 4))
                        + Filter.Contains(TestGameRekord.TITLE, "2")
            )
            .getOrNull().orEmpty()
        assertEquals(expected = 2, actual = games5.size)
        assertEquals(expected = "Doom 2", actual = games5[0].title)
        assertEquals(expected = "Silent Hill 2", actual = games5[1].title)
    }

    open fun check_RekordsStore_query_method_with_orderBy() = test {
        // query is ordered by title then by release date
        // but all titles are unique so ascending order for release date doesn't take into account
        val games1: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                filter = Filter.Contains(TestGameRekord.TITLE, "Silent Hill"),
                orderBy = listOf(
                    Order.Descending(TestGameRekord.TITLE),
                    Order.Ascending(TestGameRekord.RELEASE_DATE)
                )
            )
            .getOrNull().orEmpty()
        assertEquals(expected = "Silent Hill 3", actual = games1[0].title)
        assertEquals(expected = LocalDate(2003, 5, 23), actual = games1[0].releaseDate)
        assertEquals(expected = "Silent Hill 2", actual = games1[1].title)
        assertEquals(expected = LocalDate(2001, 9, 25), actual = games1[1].releaseDate)
        assertEquals(expected = "Silent Hill", actual = games1[2].title)
        assertEquals(expected = LocalDate(1999, 2, 24), actual = games1[2].releaseDate)
        // makes the same title for all three silent hill games to test multiple orderBy
        rekordsStore.put<TestGameRekord>(
            values = mapOf(TestGameRekord.TITLE to "Silent Hill".asRekordValue()),
            filter = Filter.Contains(TestGameRekord.TITLE, "Silent Hill"),
        )
        // query is ordered by title then by release date
        // now we have several game rekords with the same title so we can test the second ordering by date
        val games2: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                filter = Filter.Equals(TestGameRekord.TITLE, "Silent Hill"),
                orderBy = listOf(
                    Order.Descending(TestGameRekord.TITLE),
                    Order.Ascending(TestGameRekord.RELEASE_DATE)
                )
            )
            .getOrNull().orEmpty()
        assertEquals(expected = "Silent Hill", actual = games2[0].title)
        assertEquals(expected = LocalDate(1999, 2, 24), actual = games2[0].releaseDate)
        assertEquals(expected = "Silent Hill", actual = games2[1].title)
        assertEquals(expected = LocalDate(2001, 9, 25), actual = games2[1].releaseDate)
        assertEquals(expected = "Silent Hill", actual = games2[2].title)
        assertEquals(expected = LocalDate(2003, 5, 23), actual = games2[2].releaseDate)
    }

    open fun check_RekordsStore_query_method_with_limit_and_offset() = test {
        val games1: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(limit = 3, offset = 2)
            .getOrNull().orEmpty()
        assertEquals(expected = 3, actual = games1.size)
        assertEquals(expected = "Silent Hill", actual = games1[0].title)
        assertEquals(expected = "Silent Hill 2", actual = games1[1].title)
        assertEquals(expected = "Silent Hill 3", actual = games1[2].title)
        val games2: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                filter = Filter.Contains(TestGameRekord.TITLE, "Silent Hill"),
                orderBy = listOf(Order.Descending(TestGameRekord.TITLE)),
                limit = 2,
            )
            .getOrNull().orEmpty()
        assertEquals(expected = 2, actual = games2.size)
        assertEquals(expected = "Silent Hill 3", actual = games2[0].title)
        assertEquals(expected = "Silent Hill 2", actual = games2[1].title)
    }

    open fun check_RekordsStore_delete_method() = test {
        // before deleting
        val games1: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>()
            .getOrNull().orEmpty()
        assertEquals(expected = 5, actual = games1.size)
        assertEquals(expected = "Doom", actual = games1[0].title)
        assertEquals(expected = "Doom 2", actual = games1[1].title)
        assertEquals(expected = "Silent Hill", actual = games1[2].title)
        assertEquals(expected = "Silent Hill 2", actual = games1[3].title)
        assertEquals(expected = "Silent Hill 3", actual = games1[4].title)
        // delete all games where title contains "Doom"
        rekordsStore.delete<TestGameRekord>(
            filter = Filter.Contains(TestGameRekord.TITLE, "Doom"),
        )
        val games2: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>()
            .getOrNull().orEmpty()
        assertEquals(expected = 3, actual = games2.size)
        assertEquals(expected = "Silent Hill", actual = games2[0].title)
        assertEquals(expected = "Silent Hill 2", actual = games2[1].title)
        assertEquals(expected = "Silent Hill 3", actual = games2[2].title)
        // delete game with "Silent Hill 2" title
        rekordsStore.delete<TestGameRekord>(
            filter = Filter.Equals(TestGameRekord.TITLE, "Silent Hill 2"),
        )
        val games3: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>()
            .getOrNull().orEmpty()
        assertEquals(expected = 2, actual = games3.size)
        assertEquals(expected = "Silent Hill", actual = games3[0].title)
        assertEquals(expected = "Silent Hill 3", actual = games3[1].title)
        // delete all remaining games
        rekordsStore.delete<TestGameRekord>()
        val games4: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>()
            .getOrNull().orEmpty()
        assertEquals(expected = 0, actual = games4.size)
    }

    open fun check_RekordsStore_count_method() = test {
        val count1: Int = rekordsStore
            .count<TestGameRekord>()
            .getOrDefault(0)
        assertEquals(expected = 5, actual = count1)
        val count2: Int = rekordsStore
            .count<TestGameRekord>(filter = Filter.Contains(TestGameRekord.TITLE, "Doom"))
            .getOrDefault(0)
        assertEquals(expected = 2, actual = count2)
    }

    open fun check_composite_state_is_read_correctly() = test {
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrNull()
        assertEquals(expected = 3, actual = doom?.state?.id)
        assertEquals(expected = "Completed", actual = doom?.state?.name)
    }

    open fun check_composite_list_properties_are_read_correctly() = test {
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrNull()
        assertEquals(expected = 4, actual = doom?.properties?.size)
        val fps = doom?.properties?.find { it.id == "fps" }
        assertEquals(expected = TestGamePropertyRekord.TYPE_GENRE, actual = fps?.type)
        assertEquals(expected = "FPS", actual = fps?.title)
    }

    // Composites are stored in their own table without duplication:
    // 5 games reference states id=3 (×3), id=2 (×1), id=1 (×1) → exactly 3 distinct state rekords
    open fun check_composite_state_normalization() = test {
        val count = rekordsStore.count<TestGameStateRekord>().getOrDefault(0)
        assertEquals(expected = 3, actual = count)
    }

    open fun check_composite_update_is_reflected_in_parent() = test {
        rekordsStore.put<TestGameStateRekord>(
            values = mapOf(TestGameStateRekord.NAME to "Finished".asRekordValue()),
            filter = Filter.Equals(TestGameStateRekord.ID, 3),
        )
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrNull()
        assertEquals(expected = 3, actual = doom?.state?.id)
        assertEquals(expected = "Finished", actual = doom?.state?.name)
    }

    // Doom (id=1), Doom 2 (id=2), Silent Hill (id=3) all share state id=3 "Completed"
    open fun check_composite_shared_update_is_reflected_in_multiple_parents() = test {
        rekordsStore.put<TestGameStateRekord>(
            values = mapOf(TestGameStateRekord.NAME to "Finished".asRekordValue()),
            filter = Filter.Equals(TestGameStateRekord.ID, 3),
        )
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrNull()
        val doom2 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 2)).getOrNull()
        val silentHill = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 3)).getOrNull()
        assertEquals(expected = "Finished", actual = doom?.state?.name)
        assertEquals(expected = "Finished", actual = doom2?.state?.name)
        assertEquals(expected = "Finished", actual = silentHill?.state?.name)
    }

    // Doom (id=1) and Doom 2 (id=2) share the same "fps" genre property
    open fun check_composite_list_update_is_reflected_in_parent() = test {
        rekordsStore.put<TestGamePropertyRekord>(
            values = mapOf(TestGamePropertyRekord.TITLE to "First Person Shooter".asRekordValue()),
            filter = Filter.Equals(TestGamePropertyRekord.ID, "fps"),
        )
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrNull()
        val doom2 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 2)).getOrNull()
        assertEquals(expected = "First Person Shooter", actual = doom?.properties?.find { it.id == "fps" }?.title)
        assertEquals(expected = "First Person Shooter", actual = doom2?.properties?.find { it.id == "fps" }?.title)
    }

    open fun check_composite_insert_with_new_state() = test {
        val halfLife = TestGameRekord(
            id = 6,
            title = "Half-Life",
            releaseDate = LocalDate(1998, 11, 19),
            state = TestGameStateRekord(id = 4, name = "Dropped"),
            properties = listOf(
                TestGamePropertyRekord(
                    id = "fps",
                    type = TestGamePropertyRekord.TYPE_GENRE,
                    title = "FPS",
                    locale = "en",
                ),
            ),
        )
        rekordsStore.putRekord(halfLife)
        val result = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 6)).getOrNull()
        assertEquals(expected = 6, actual = result?.id)
        assertEquals(expected = 4, actual = result?.state?.id)
        assertEquals(expected = "Dropped", actual = result?.state?.name)
        assertEquals(expected = 1, actual = result?.properties?.size)
    }

    open fun check_filter_by_nested_field() = test {
        // filter by composite field value (id) — 3 games have state id=3
        val byId: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                Filter.Nested(TestGameRekord.STATE, Filter.Equals(TestGameStateRekord.ID, 3))
            )
            .getOrNull().orEmpty()
        assertEquals(expected = 3, actual = byId.size)
        assertEquals(expected = "Doom", actual = byId[0].title)
        assertEquals(expected = "Doom 2", actual = byId[1].title)
        assertEquals(expected = "Silent Hill", actual = byId[2].title)

        // filter by composite non-id field (name) — 1 game has state "Playing"
        val byName: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                Filter.Nested(TestGameRekord.STATE, Filter.Equals(TestGameStateRekord.NAME, "Playing"))
            )
            .getOrNull().orEmpty()
        assertEquals(expected = 1, actual = byName.size)
        assertEquals(expected = "Silent Hill 2", actual = byName[0].title)

        // filter by composite list field — games that have a property with type "genre"
        val byProperty: List<TestGameRekord> = rekordsStore
            .getRekords<TestGameRekord>(
                Filter.Nested(TestGameRekord.PROPERTIES, Filter.Equals(TestGamePropertyRekord.TYPE, TestGamePropertyRekord.TYPE_GENRE))
            )
            .getOrNull().orEmpty()
        assertEquals(expected = 5, actual = byProperty.size)

        // count with nested filter
        val count: Int = rekordsStore
            .count<TestGameRekord>(
                filter = Filter.Nested(TestGameRekord.STATE, Filter.Equals(TestGameStateRekord.NAME, "Wishlist"))
            )
            .getOrDefault(0)
        assertEquals(expected = 1, actual = count)
    }

    open fun check_composite_delete_parent_does_not_affect_shared_state() = test {
        // Doom (id=1) and Doom 2 (id=2) share state id=3
        rekordsStore.delete<TestGameRekord>(filter = Filter.Equals(TestGameRekord.ID, 1))
        val doom2 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 2)).getOrNull()
        assertEquals(expected = 3, actual = doom2?.state?.id)
        assertEquals(expected = "Completed", actual = doom2?.state?.name)
    }

    // Unique (id, type) pairs across all 5 games:
    // fps, pc, id_software, gt_interactive, doom, silent_hill, survival_horror,
    // playstation, playstation_2, team_silent, konami = 11 unique rekords, no duplicates
    open fun check_composite_list_normalization() = test {
        val count = rekordsStore.count<TestGamePropertyRekord>().getOrDefault(0)
        assertEquals(expected = 11, actual = count)
    }

    // Silent Hill (id=3), Silent Hill 2 (id=4), Silent Hill 3 (id=5) all share "survival_horror" property
    open fun check_composite_list_shared_update_is_reflected_in_multiple_parents() = test {
        rekordsStore.put<TestGamePropertyRekord>(
            values = mapOf(TestGamePropertyRekord.TITLE to "Survival Horror (updated)".asRekordValue()),
            filter = Filter.Equals(TestGamePropertyRekord.ID, "survival_horror"),
        )
        val sh1 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 3)).getOrNull()
        val sh2 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 4)).getOrNull()
        val sh3 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5)).getOrNull()
        val title1 = sh1?.properties?.find { it.id == "survival_horror" }?.title
        val title2 = sh2?.properties?.find { it.id == "survival_horror" }?.title
        val title3 = sh3?.properties?.find { it.id == "survival_horror" }?.title
        assertEquals(expected = "Survival Horror (updated)", actual = title1)
        assertEquals(expected = "Survival Horror (updated)", actual = title2)
        assertEquals(expected = "Survival Horror (updated)", actual = title3)
    }

    // Doom (id=1) is stored with four properties and saved again with one of them
    open fun check_composite_list_is_replaced_by_the_one_written() = test {
        rekordsStore.putRekord(
            TestGameRekord(
                id = 1,
                title = "Doom",
                releaseDate = LocalDate(1993, 12, 10),
                remasterDate = LocalDate(2019, 7, 26),
                state = TestGameStateRekord(id = 3, name = "Completed"),
                properties = listOf(
                    TestGamePropertyRekord(
                        id = "fps",
                        type = TestGamePropertyRekord.TYPE_GENRE,
                        title = "FPS",
                        locale = "en",
                    ),
                ),
            ),
        )
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrNull()
        assertEquals(expected = listOf("fps"), actual = doom?.properties?.map { it.id })
        // The properties it no longer holds are still shared with Doom 2 (id=2)
        val doom2 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 2)).getOrNull()
        assertEquals(expected = "PC", actual = doom2?.properties?.find { it.id == "pc" }?.title)
    }

    open fun check_composite_list_delete_parent_does_not_affect_shared_properties() = test {
        // Silent Hill (id=3) shares survival_horror and team_silent with Silent Hill 2 (id=4) and 3 (id=5)
        rekordsStore.delete<TestGameRekord>(filter = Filter.Equals(TestGameRekord.ID, 3))
        val sh2 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 4)).getOrNull()
        assertEquals(expected = 5, actual = sh2?.properties?.size)
        assertEquals(expected = "Survival Horror", actual = sh2?.properties?.find { it.id == "survival_horror" }?.title)
        assertEquals(expected = "Team Silent", actual = sh2?.properties?.find { it.id == "team_silent" }?.title)
    }

    /**
     * A composite is the rekord its ids name, not the values it was written with: writing it again
     * with a value changed updates the one stored rather than filing a second one under the same
     * ids, and every parent holding those ids is left seeing the change.
     */
    open fun check_composite_with_known_ids_is_updated_not_duplicated() = test {
        val states = rekordsStore.count<TestGameStateRekord>().getOrDefault(0)
        // Doom (id=1) and Doom 2 (id=2) both hold the state with id=3, seeded as "Completed".
        rekordsStore.putRekord(
            TestGameRekord(
                id = 1,
                title = "Doom",
                releaseDate = LocalDate(1993, 12, 10),
                state = TestGameStateRekord(id = 3, name = "Beaten"),
                properties = emptyList(),
            )
        )
        assertEquals(expected = states, actual = rekordsStore.count<TestGameStateRekord>().getOrDefault(0))
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrNull()
        val doom2 = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 2)).getOrNull()
        assertEquals(expected = "Beaten", actual = doom?.state?.name)
        assertEquals(expected = "Beaten", actual = doom2?.state?.name)
    }

    /**
     * A store given up is a store that works again: what an editor was holding is acquired anew by
     * the operation that follows the close, and the rekords written before it are still there.
     */
    open fun check_store_is_usable_after_close() = test {
        rekordsStore.close()
        // The read reopens what the close gave up, and finds what was seeded through it.
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
        assertEquals(expected = "Doom", actual = doom.getOrNull()?.title)

        // Writes land after a reopen too, and closing what is already closed is not an error.
        rekordsStore.put<TestGameRekord>(
            values = mapOf(TestGameRekord.TITLE to "Doom (reopened)".asRekordValue()),
            filter = Filter.Equals(TestGameRekord.ID, 1),
        )
        rekordsStore.close()
        rekordsStore.close()
        val reopenedDoom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
        assertEquals(expected = "Doom (reopened)", actual = reopenedDoom.getOrNull()?.title)
    }

    /**
     * Every operation of a transaction is there once it completes: writes, updates and deletes,
     * down to the composites a new rekord brings along.
     */
    open fun check_transaction_applies_every_operation_once_it_completes() = test {
        rekordsStore.transaction {
            putRekord(quake())
            put<TestGameRekord>(
                values = mapOf(TestGameRekord.SCORE to 9.5f.asRekordValue()),
                filter = Filter.Equals(TestGameRekord.ID, 1),
            )
            delete<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5))
        }.getOrThrow()

        val quake = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 10))
            .getOrNull()
        assertEquals(expected = "Abandoned", actual = quake?.state?.name)
        assertEquals(expected = listOf("quake"), actual = quake?.properties?.map { it.id })
        val doom = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
            .getOrNull()
        assertEquals(expected = 9.5f, actual = doom?.score)
        val silentHill3 = rekordsStore
            .getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5))
            .getOrNull()
        assertEquals(expected = null, actual = silentHill3)
        assertEquals(expected = 5, actual = rekordsStore.count<TestGameRekord>().getOrNull())
    }

    /** What a transaction has written is what it reads back, before anything is committed. */
    open fun check_transaction_reads_back_what_it_has_written() = test {
        val seen = rekordsStore.transaction {
            putRekord(quake())
            delete<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5))
            listOf(
                getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 10))?.title,
                getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5))?.title,
                count<TestGameRekord>().toString(),
            )
        }.getOrThrow()

        assertEquals(expected = listOf("Quake", null, "5"), actual = seen)
    }

    /**
     * A transaction that fails leaves nothing it did behind, whatever it did: rekords written,
     * composites shared with other rekords updated, composite lists replaced, rekords deleted -
     * every one of a type included.
     */
    open fun check_transaction_is_rolled_back_when_it_fails() = test {
        val before = describeStore()

        val result = rekordsStore.transaction {
            putRekord(quake())
            // Doom's state is shared with Doom 2 and Silent Hill, and its genre with Doom 2.
            putRekord(
                TestGameRekord(
                    id = 1,
                    title = "Doom",
                    releaseDate = LocalDate(1993, 12, 10),
                    state = TestGameStateRekord(id = 3, name = "Beaten"),
                    properties = listOf(
                        TestGamePropertyRekord(
                            id = "fps",
                            type = TestGamePropertyRekord.TYPE_GENRE,
                            title = "Shooter",
                            locale = "en",
                        ),
                    ),
                )
            )
            put<TestGameRekord>(
                values = mapOf(TestGameRekord.SCORE to 1.0f.asRekordValue()),
                filter = Filter.LessThan(TestGameRekord.ID, 4),
            )
            delete<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5))
            delete<TestGameStateRekord>()
            error("The transaction fails after its writes")
        }

        assertTrue(result.isFailure)
        assertEquals(expected = before, actual = describeStore())
        // Found by the searchable title too, which an editor indexing it has to have taken back.
        assertEquals(expected = listOf("fps"), actual = propertyIdsTitled("FPS"))
        assertEquals(expected = emptyList(), actual = propertyIdsTitled("Shooter"))
    }

    /**
     * A cancellation thrown within a transaction fails it the way any other exception does, and
     * leaves nothing it did behind either - while still reaching the caller as a cancellation.
     */
    open fun check_transaction_is_rolled_back_when_cancelled() = test {
        val before = describeStore()

        val cancellation = try {
            rekordsStore.transaction {
                putRekord(quake())
                delete<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5))
                throw CancellationException("Cancelled after its writes")
            }
            null
        } catch (e: CancellationException) {
            e
        }

        assertTrue(cancellation != null, "The cancellation did not reach the caller")
        assertEquals(expected = before, actual = describeStore())
    }

    /**
     * A cancellation reaches the caller of a transaction as one, rather than as a failed result it
     * would go on from: nothing after the call runs in a coroutine that has been cancelled.
     */
    open fun check_cancelled_transaction_cancels_its_caller() = test {
        var resumedAfterTransaction = false

        coroutineScope {
            launch {
                coroutineContext.job.cancel()
                rekordsStore.transaction { count<TestGameRekord>() }
                resumedAfterTransaction = true
            }
        }

        assertFalse(resumedAfterTransaction)
    }

    /**
     * A transaction started within another is part of it rather than one of its own: failing, it
     * fails the one it is in, and caught there, what it wrote is kept with it.
     */
    open fun check_nested_transaction_joins_the_one_it_runs_in() = test {
        val before = describeStore()

        val failed = rekordsStore.transaction {
            delete<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5))
            transaction {
                putRekord(quake())
                error("The nested transaction fails after its writes")
            }
        }
        assertTrue(failed.isFailure)
        assertEquals(expected = before, actual = describeStore())

        rekordsStore.transaction {
            try {
                transaction {
                    putRekord(quake())
                    error("The nested transaction fails after its writes")
                }
            } catch (_: IllegalStateException) {
            }
        }.getOrThrow()
        val quake = rekordsStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 10))
        assertEquals(expected = "Quake", actual = quake.getOrNull()?.title)
    }

    /**
     * A schema upgrade runs as a transaction as well: one that fails leaves the storage at the
     * version it was, with the rekords it held, for the next operation to try it again.
     */
    open fun check_failed_schema_upgrade_is_rolled_back() = test {
        val before = describeStore()
        val version = rekordsEditor.schemaEditor.version()
        val upgradingStore = RekordsStore(
            schema = TestUpgradingSchema(version = version + 1) {
                putRekord(quake())
                delete<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 5))
                error("The upgrade fails after its writes")
            },
            editor = rekordsEditor,
        )

        assertTrue(upgradingStore.count<TestGameRekord>().isFailure)
        assertEquals(expected = version, actual = rekordsEditor.schemaEditor.version())
        assertEquals(expected = before, actual = describeStore())
    }

    private fun quake() = TestGameRekord(
        id = 10,
        title = "Quake",
        releaseDate = LocalDate(1996, 6, 22),
        state = TestGameStateRekord(id = 4, name = "Abandoned"),
        properties = listOf(
            TestGamePropertyRekord(
                id = "quake",
                type = TestGamePropertyRekord.TYPE_SERIES,
                title = "Quake",
                locale = "en",
            ),
        ),
    )

    /**
     * Everything the store holds, read into lines that can be compared from one moment to the
     * next - the rekords themselves are neither comparable nor safe to keep, an editor being free
     * to hand out what it goes on changing.
     */
    private suspend fun describeStore(): List<String> {
        val games = rekordsStore
            .getRekords<TestGameRekord>(orderBy = listOf(Order.Ascending(TestGameRekord.ID)))
            .getOrThrow()
            .map { game ->
                val properties = game.properties.map { it.id }.sorted()
                "game ${game.id} ${game.title} ${game.score} ${game.state.id} $properties"
            }
        val states = rekordsStore
            .getRekords<TestGameStateRekord>(orderBy = listOf(Order.Ascending(TestGameStateRekord.ID)))
            .getOrThrow()
            .map { state -> "state ${state.id} ${state.name}" }
        val properties = rekordsStore.getRekords<TestGamePropertyRekord>()
            .getOrThrow()
            .map { property -> "property ${property.id} ${property.type} ${property.title}" }
            .sorted()
        return games + states + properties
    }

    private suspend fun propertyIdsTitled(title: String): List<String> =
        rekordsStore
            .getRekords<TestGamePropertyRekord>(Filter.Equals(TestGamePropertyRekord.TITLE, title))
            .getOrThrow()
            .map { it.id }
            .sorted()
}
