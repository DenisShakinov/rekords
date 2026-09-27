package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord

@Rekord(type = "game_state")
class TestGameStateRekord(
    @Field(name = ID, id = true)
    val id: Int,
    /**
     * Searchable and nullable on purpose: an index has to hold rekords without the value too, and
     * a SQL table can only be given a column it is added once it is nullable.
     */
    @Field(name = NAME, searchable = true)
    val name: String?,
) {
    companion object {
        const val ID = "id"
        const val NAME = "name"
    }
}