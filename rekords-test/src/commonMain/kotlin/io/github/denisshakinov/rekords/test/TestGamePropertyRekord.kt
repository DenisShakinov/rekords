package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Rekord

@Rekord(type = "game_property")
class TestGamePropertyRekord(
    @Field(name = ID, id = true)
    val id: String,
    @Field(name = TYPE, id = true)
    val type: Int,
    /** Searchable on purpose: an editor is free to index such a field, and has to keep it true. */
    @Field(name = TITLE, searchable = true)
    val title: String,
    @Field(name = LOCALE)
    val locale: String,
) {
    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val LOCALE = "locale"
        const val TYPE = "type"

        const val TYPE_SERIES = 0
        const val TYPE_GENRE = 1
        const val TYPE_PLATFORM = 2
        const val TYPE_MODE = 3
        const val TYPE_DEVELOPER = 4
        const val TYPE_PUBLISHER = 5
    }
}