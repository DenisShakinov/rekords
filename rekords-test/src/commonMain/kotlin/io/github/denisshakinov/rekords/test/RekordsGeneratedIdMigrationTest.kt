package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.removeRekordType
import io.github.denisshakinov.rekords.core.renameRekordType
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Runs stores of two versions of a schema over [rekordsEditor], the second renaming a rekord type
 * whose ids are generated and removing another: the numbers given go with the type renamed, and
 * with the one removed, which a type added under its name starts anew without.
 */
abstract class RekordsGeneratedIdMigrationTest(private val rekordsEditor: RekordsEditor) {

    open fun check_numbers_given_go_with_a_rekord_type_renamed_or_removed() = runTest {
        val before = RekordsStore(TestGeneratedIdSchemaV1(), rekordsEditor)
        before.putRekords(listOf(TestOldPlayerRekord(name = "First"), TestOldPlayerRekord(name = "Second"))).getOrThrow()
        before.delete<TestOldPlayerRekord>(Filter.Equals(TestOldPlayerRekord.ID, 2L)).getOrThrow()
        before.putRekords(listOf(TestDroppedRekord(name = "First"), TestDroppedRekord(name = "Second"))).getOrThrow()

        val after = RekordsStore(TestGeneratedIdSchemaV2(), rekordsEditor)
        val third = after.putRekord(TestPlayerRekord(name = "Third")).getOrThrow()
        val readded = after.putRekord(TestReaddedRekord(name = "Again")).getOrThrow()

        assertEquals(expected = 3L, actual = third.id)
        assertEquals(expected = 1L, actual = readded.id)
    }
}

/** A player, as the first version of the schema has its type named. */
@Rekord(type = "gidm_old_player")
data class TestOldPlayerRekord(
    @Field(name = ID, id = true, generated = true) val id: Long = 0,
    @Field(name = NAME) val name: String,
) {
    companion object {
        const val ID = "id"
        const val NAME = "name"
    }
}

/** A player, as the second version of the schema has its type renamed. */
@Rekord(type = "gidm_player")
data class TestPlayerRekord(
    @Field(name = TestOldPlayerRekord.ID, id = true, generated = true) val id: Long = 0,
    @Field(name = TestOldPlayerRekord.NAME) val name: String,
)

/** A rekord type the second version of the schema removes. */
@Rekord(type = "gidm_dropped")
data class TestDroppedRekord(
    @Field(name = TestOldPlayerRekord.ID, id = true, generated = true) val id: Long = 0,
    @Field(name = TestOldPlayerRekord.NAME) val name: String,
)

/** A rekord type the second version of the schema adds under the name of the one it removes. */
@Rekord(type = "gidm_dropped")
data class TestReaddedRekord(
    @Field(name = TestOldPlayerRekord.ID, id = true, generated = true) val id: Long = 0,
    @Field(name = TestOldPlayerRekord.NAME) val name: String,
)

class TestGeneratedIdSchemaV1 : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(TestOldPlayerRekord::class, TestDroppedRekord::class)

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}

class TestGeneratedIdSchemaV2 : RekordsSchema {

    override val version: Int = 2

    override val rekordTypes: List<KClass<*>> = listOf(TestPlayerRekord::class, TestReaddedRekord::class)

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            renameRekordType<TestPlayerRekord>("gidm_old_player")
            removeRekordType<TestReaddedRekord>()
            addRekordType(TestReaddedRekord::class)
        }
    }
}
