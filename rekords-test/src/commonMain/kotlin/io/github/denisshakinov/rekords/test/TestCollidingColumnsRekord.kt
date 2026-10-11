package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import kotlin.reflect.KClass

/**
 * A rekord a SQL table cannot be made for: the state it holds is referred to by a column named
 * after the field and the id of the state, `state_id`, which is the name of its other field too.
 */
@Rekord(type = "colliding_columns")
class TestCollidingColumnsRekord(
    @Field(name = ID, id = true)
    val id: Int,
    @Field(name = STATE_ID)
    val stateId: Int,
    @Field(name = STATE)
    val state: TestGameStateRekord,
) {
    companion object {
        const val ID = "id"
        const val STATE_ID = "state_id"
        const val STATE = "state"
    }
}

class TestCollidingColumnsSchema : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(
        TestCollidingColumnsRekord::class,
        TestGameStateRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}
