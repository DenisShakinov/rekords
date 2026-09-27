@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.indexeddb

import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.InternalRekordsApi
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.addField
import io.github.denisshakinov.rekords.core.asRekordValue
import io.github.denisshakinov.rekords.core.put
import io.github.denisshakinov.rekords.core.removeField
import io.github.denisshakinov.rekords.core.renameField
import io.github.denisshakinov.rekords.test.TestGamePropertyRekord
import io.github.denisshakinov.rekords.test.TestGameRekord
import io.github.denisshakinov.rekords.test.TestGameStateRekord
import io.github.denisshakinov.rekords.test.TestRekordsSchema
import io.github.denisshakinov.rekords.test.TestUpgradingSchema
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a migration does to the rekords IndexedDB keeps, which the shared suite cannot ask about:
 * the in-memory editor, which it runs against as well, never migrates.
 */
class IndexedDBMigrationTest {

    private val rekordsEditor: RekordsEditor = IndexedDBRekordsEditor(nextMigrationDatabaseName())

    private suspend fun storeWithDoom(): RekordsStore {
        val store = RekordsStore(TestRekordsSchema(), rekordsEditor)
        store.putRekord(doom()).getOrThrow()
        return store
    }

    private suspend fun upgrade(upgrade: suspend RekordsMigrationEditor.() -> Unit): RekordsStore {
        val version = rekordsEditor.schemaEditor.version()
        return RekordsStore(TestUpgradingSchema(version = version + 1, upgrade), rekordsEditor)
    }

    /**
     * A field a migration adds is given, in the rekords already stored, the default value it is
     * added with - or, given none, the value of its type that stands for none where it cannot be
     * null, and no value where it can.
     */
    @Test
    fun field_added_by_a_migration_is_given_its_default_value() = runTest {
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

        val doom = upgradedStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrThrow()
        assertEquals(expected = LocalDate(1970, 1, 1), actual = doom?.releaseDate)
        assertEquals(expected = false, actual = doom?.finished)
        assertEquals(expected = 7.5f, actual = doom?.score)
        assertEquals(expected = null, actual = doom?.remasterDate)
        assertEquals(expected = listOf(""), actual = doom?.properties?.map { it.locale })
        assertEquals(expected = "Unknown", actual = doom?.state?.name)
    }

    /** A default value the field cannot hold fails the migration, which leaves the storage as it was. */
    @Test
    fun default_value_the_field_cannot_hold_fails_the_migration() = runTest {
        val store = storeWithDoom()
        val version = rekordsEditor.schemaEditor.version()
        val upgradedStore = upgrade {
            removeField<TestGameRekord>(TestGameRekord.SCORE)
            addField<TestGameRekord>(TestGameRekord.SCORE, "high")
        }

        val failure = upgradedStore.count<TestGameRekord>().exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "Failed with $failure")
        assertEquals(expected = version, actual = rekordsEditor.schemaEditor.version())
        val doom = store.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1))
        assertEquals(expected = 9.5f, actual = doom.getOrThrow()?.score)
    }

    /**
     * A field renamed keeps its values, and a searchable one its index: the rekords are found by
     * it afterwards, and not by the values they held before.
     */
    @Test
    fun field_renamed_by_a_migration_keeps_its_values_and_index() = runTest {
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

    /** A rekord type renamed keeps its rekords, and the parents referring to them still see them. */
    @Test
    fun rekord_type_renamed_by_a_migration_keeps_its_rekords() = runTest {
        storeWithDoom()
        val upgradedStore = upgrade {
            renameRekordType(PROPERTY_TYPE, "property_before")
            renameRekordType("property_before", PROPERTY_TYPE)
        }

        val doom = upgradedStore.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrThrow()
        assertEquals(expected = listOf("fps"), actual = doom?.properties?.map { it.id })
        val found = upgradedStore
            .getRekords<TestGamePropertyRekord>(Filter.Equals(TestGamePropertyRekord.TITLE, "FPS"))
            .getOrThrow()
        assertEquals(expected = listOf("fps"), actual = found.map { it.id })
    }

    /** A [Long] a JavaScript number cannot hold exactly is kept exactly all the same. */
    @Test
    fun long_beyond_what_a_number_holds_is_kept_exactly() = runTest {
        val store = RekordsStore(TestRekordsSchema(), rekordsEditor)
        store.putRekord(doom(steamId = Long.MAX_VALUE)).getOrThrow()

        val doom = store.getRekord<TestGameRekord>(Filter.Equals(TestGameRekord.ID, 1)).getOrThrow()
        assertEquals(expected = Long.MAX_VALUE, actual = doom?.steamId)
    }

    private fun doom(steamId: Long? = null) = TestGameRekord(
        id = 1,
        title = "Doom",
        releaseDate = LocalDate(1993, 12, 10),
        remasterDate = LocalDate(2019, 7, 26),
        steamId = steamId,
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
    }
}

private var migrationDatabaseCount = 0

private fun nextMigrationDatabaseName(): String {
    installFakeIndexedDB()
    return "rekords-migration-test-${migrationDatabaseCount++}"
}
