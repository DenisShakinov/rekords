@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.addField
import io.github.denisshakinov.rekords.core.addRekordType
import io.github.denisshakinov.rekords.core.asRekordValue
import io.github.denisshakinov.rekords.core.put
import io.github.denisshakinov.rekords.core.removeField
import io.github.denisshakinov.rekords.core.renameField
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a migration does to the rekords [rekordsEditor] keeps: each store is given the schema at the
 * version after the one the storage is at, so that the tests of a run sharing one storage upgrade
 * it each in turn.
 */
abstract class RekordsMigrationTest(private val rekordsEditor: RekordsEditor) {

    private suspend fun storeWithDoom(): RekordsStore {
        val store = RekordsStore(TestRekordsSchema(), rekordsEditor)
        store.delete<TestGameRekord>().getOrThrow()
        store.delete<TestGamePropertyRekord>().getOrThrow()
        store.putRekord(doom()).getOrThrow()
        return store
    }

    private suspend fun upgrade(upgrade: suspend RekordsMigrationEditor.() -> Unit): RekordsStore {
        val version = rekordsEditor.schemaEditor.version()
        return RekordsStore(TestUpgradingSchema(version = version + 1, upgrade), rekordsEditor)
    }

    private suspend fun RekordsStore.doom(): TestGameRekord? =
        getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrThrow()

    /**
     * A field a migration adds is given, in the rekords already stored, the default value it is
     * added with - or, given none, the value of its type that stands for none where it cannot be
     * null, and no value where it can.
     */
    open fun check_field_added_by_a_migration_is_given_its_default_value() = runTest {
        storeWithDoom()
        val upgradedStore = upgrade {
            removeField<TestGameRekord>(TestGameRekord.RELEASE_DATE)
            addField<TestGameRekord>(TestGameRekord.RELEASE_DATE)
            removeField<TestGameRekord>(TestGameRekord.FINISHED)
            addField<TestGameRekord>(TestGameRekord.FINISHED)
            removeField<TestGameRekord>(TestGameRekord.SCORE)
            addField<TestGameRekord>(TestGameRekord.SCORE, 7.5f)
            removeField<TestGameRekord>(TestGameRekord.REMASTER_DATE)
            addField<TestGameRekord>(TestGameRekord.REMASTER_DATE)
            removeField<TestGamePropertyRekord>(TestGamePropertyRekord.LOCALE)
            addField<TestGamePropertyRekord>(TestGamePropertyRekord.LOCALE)
            removeField<TestGameStateRekord>(TestGameStateRekord.NAME)
            addField<TestGameStateRekord>(TestGameStateRekord.NAME, "Unknown")
        }

        val doom = upgradedStore.doom()
        assertEquals(expected = LocalDate(1970, 1, 1), actual = doom?.releaseDate)
        assertEquals(expected = false, actual = doom?.finished)
        assertEquals(expected = 7.5f, actual = doom?.score)
        assertEquals(expected = null, actual = doom?.remasterDate)
        assertEquals(expected = listOf(""), actual = doom?.properties?.map { it.locale })
        assertEquals(expected = "Unknown", actual = doom?.state?.name)
    }

    /** A default value the field cannot hold fails the migration, which leaves the storage as it was. */
    open fun check_default_value_the_field_cannot_hold_fails_the_migration() = runTest {
        val store = storeWithDoom()
        val version = rekordsEditor.schemaEditor.version()
        val upgradedStore = upgrade {
            removeField<TestGameRekord>(TestGameRekord.SCORE)
            addField<TestGameRekord>(TestGameRekord.SCORE, "high")
        }

        val failure = upgradedStore.count<TestGameRekord>().exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "Failed with $failure")
        assertEquals(expected = version, actual = rekordsEditor.schemaEditor.version())
        assertEquals(expected = 9.5f, actual = store.doom()?.score)
    }

    /**
     * A field renamed keeps its values, and a searchable one its index: the rekords are found by
     * it afterwards, and not by the values they held before.
     */
    open fun check_field_renamed_by_a_migration_keeps_its_values_and_index() = runTest {
        storeWithDoom()
        val upgradedStore = upgrade {
            for (field in listOf(TestGamePropertyRekord.LOCALE, TestGamePropertyRekord.TITLE)) {
                renameField(PROPERTY_TYPE, field, "${field}_before")
                renameField(PROPERTY_TYPE, "${field}_before", field)
            }
            put<TestGamePropertyRekord>(
                values = mapOf(TestGamePropertyRekord.TITLE to "Shooter".asRekordValue()),
                filter = Filter.Equals(TestGamePropertyRekord.ID, "fps"),
            )
        }

        val shooters = upgradedStore
            .getRekords<TestGamePropertyRekord>(Filter.Equals(TestGamePropertyRekord.TITLE, "Shooter"))
            .getOrThrow()
        assertEquals(expected = listOf("fps"), actual = shooters.map { it.id })
        assertEquals(expected = listOf("en"), actual = shooters.map { it.locale })
        val fps = upgradedStore
            .getRekords<TestGamePropertyRekord>(Filter.Equals(TestGamePropertyRekord.TITLE, "FPS"))
            .getOrThrow()
        assertEquals(expected = emptyList(), actual = fps)
    }

    /**
     * A rekord type renamed keeps its rekords and its lists, and the parents referring to them
     * still see them - renamed to a name the schema has or from one, as each of these is.
     */
    open fun check_rekord_type_renamed_by_a_migration_keeps_its_rekords() = runTest {
        storeWithDoom()
        val upgradedStore = upgrade {
            renameRekordType(PROPERTY_TYPE, "property_before")
            renameRekordType("property_before", PROPERTY_TYPE)
            renameRekordType(STATE_TYPE, "state_before")
            renameRekordType("state_before", STATE_TYPE)
            renameRekordType(GAME_TYPE, "game_before")
            renameRekordType("game_before", GAME_TYPE)
        }

        val doom = upgradedStore.doom()
        assertEquals(expected = listOf("fps"), actual = doom?.properties?.map { it.id })
        assertEquals(expected = "Completed", actual = doom?.state?.name)
        val found = upgradedStore
            .getRekords<TestGamePropertyRekord>(Filter.Equals(TestGamePropertyRekord.TITLE, "FPS"))
            .getOrThrow()
        assertEquals(expected = listOf("fps"), actual = found.map { it.id })
    }

    /** A rekord type removed takes its rekords with it, and the parents no longer hold them. */
    open fun check_rekord_type_removed_by_a_migration_takes_its_rekords() = runTest {
        storeWithDoom()
        val upgradedStore = upgrade {
            removeRekordType(PROPERTY_TYPE)
            addRekordType<TestGamePropertyRekord>()
        }

        assertEquals(expected = 0, actual = upgradedStore.count<TestGamePropertyRekord>().getOrThrow())
        assertEquals(expected = emptyList(), actual = upgradedStore.doom()?.properties)
    }

    /**
     * A migration that fails takes back every change it made to the storage before - to fields and
     * rekord types as much as to rekords - and the version it was to upgrade to.
     */
    open fun check_failed_migration_takes_back_the_changes_it_made() = runTest {
        val store = storeWithDoom()
        val version = rekordsEditor.schemaEditor.version()
        val upgradedStore = upgrade {
            renameField(PROPERTY_TYPE, TestGamePropertyRekord.TITLE, "title_before")
            removeField<TestGameRekord>(TestGameRekord.SCORE)
            addField<TestGameRekord>(TestGameRekord.SCORE, 1f)
            renameRekordType(STATE_TYPE, "state_before")
            removeRekordType(PROPERTY_TYPE)
            error("The upgrade fails after its changes")
        }

        assertTrue(upgradedStore.count<TestGameRekord>().isFailure)
        assertEquals(expected = version, actual = rekordsEditor.schemaEditor.version())
        assertEquals(expected = doom().describe(), actual = store.doom()?.describe())
        val found = store
            .getRekords<TestGamePropertyRekord>(Filter.Equals(TestGamePropertyRekord.TITLE, "FPS"))
            .getOrThrow()
        assertEquals(expected = listOf("fps"), actual = found.map { it.id })
    }

    /** What a game holds, as a line to compare: the rekords are no data classes. */
    private fun TestGameRekord.describe(): String =
        "$id $title $releaseDate $remasterDate $finished $score ${state.id} ${state.name} " +
            properties.joinToString { "${it.id} ${it.type} ${it.title} ${it.locale}" }

    private fun doom() = TestGameRekord(
        id = 1,
        title = "Doom",
        releaseDate = LocalDate(1993, 12, 10),
        remasterDate = LocalDate(2019, 7, 26),
        finished = true,
        score = 9.5f,
        state = TestGameStateRekord(id = 3, name = "Completed"),
        properties = listOf(
            TestGamePropertyRekord(
                id = "fps",
                type = TestGamePropertyRekord.TYPE_GENRE,
                title = "FPS",
                locale = "en",
            ),
        ),
    )

    private companion object {
        const val PROPERTY_TYPE = "game_property"
        const val STATE_TYPE = "game_state"
        const val GAME_TYPE = TestGameRekord.REKORD_TYPE
    }
}
