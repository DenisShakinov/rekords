package io.github.denisshakinov.rekords.test

import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import kotlin.reflect.KClass

/** The schema of [TestSecretRekord], upgraded to [version] by [upgrade]. */
class TestSecretRekordsSchema(
    override val version: Int = 1,
    private val upgrade: suspend RekordsMigrationEditor.() -> Unit = {},
) : RekordsSchema {

    override val rekordTypes: List<KClass<*>> = listOf(
        TestSecretRekord::class,
        TestSecretTagRekord::class,
        TestSecretVaultRekord::class,
    )

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = upgrade()
}
