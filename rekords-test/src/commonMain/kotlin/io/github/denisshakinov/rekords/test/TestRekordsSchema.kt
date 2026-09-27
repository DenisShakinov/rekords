package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import kotlin.reflect.KClass

class TestRekordsSchema : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(
        TestGameRekord::class,
        TestGameStateRekord::class,
        TestGamePropertyRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(
        oldVersion: Int,
        newVersion: Int
    ) {
        TODO("Not yet implemented")
    }
}