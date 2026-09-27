package io.github.denisshakinov.rekords.core

import kotlin.reflect.KClass

interface RekordsSchema {

    val version: Int

    val rekordTypes: List<KClass<*>>

    suspend fun RekordsMigrationEditor.onCreate() {}

    suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int)
}

@InternalRekordsApi
fun RekordsSchema.rekordSchema(rekordType: String): Map<String, FieldWithType> =
    rekordTypes
        .firstOrNull { it.rekordType() == rekordType }
        ?.allFields()
        ?.associateBy { it.field.name }
        .orEmpty()