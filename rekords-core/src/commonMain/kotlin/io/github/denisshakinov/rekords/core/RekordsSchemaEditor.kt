@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlin.reflect.KClass

abstract class RekordsSchemaEditor {

    private var currentSchema: RekordsSchema? = null

    var schema: RekordsSchema
        get() = checkNotNull(currentSchema) { "No schema has been set on the editor" }
        internal set(value) {
            currentSchema = value
            rekordSchemas.clear()
        }

    /**
     * Whether there is a [schema] to read. A store sets one on the editor it is built with, so an
     * editor reached on its own has none, and what the schema would have told about a rekord type
     * is not to be asked of it.
     */
    @InternalRekordsApi
    val hasSchema: Boolean get() = currentSchema != null

    /**
     * The fields of each rekord type the [schema] has been asked about. Reading them walks the
     * rekord types it declares and reflects over the one that matches, which an editor asks for
     * several times over every rekord it writes.
     */
    internal val rekordSchemas: MutableMap<String, Map<String, FieldWithType>> = mutableMapOf()

    /**
     * Returns the current version of the rekords storage schema.
     * The version is incremented each time the schema is migrated.
     */
    abstract suspend fun version(): Int

    /**
     * Updates the current version of the rekords storage schema.
     *
     * @param newVersion the new version number to set.
     */
    abstract suspend fun updateVersion(newVersion: Int)

    /**
     * Registers a new rekord type in the schema based on the given [rekordClass].
     *
     * @param rekordClass the KClass of the rekord to register.
     */
    abstract suspend fun addRekordType(rekordClass: KClass<*>)

    /**
     * Removes a rekord type and all its associated data from the schema.
     *
     * @param rekordType the type name of the rekord to remove.
     */
    abstract suspend fun removeRekordType(rekordType: String)

    /**
     * Renames an existing rekord type in the schema.
     *
     * @param oldRekordType the current type name of the rekord.
     * @param newRekordType the new type name to assign.
     */
    abstract suspend fun renameRekordType(oldRekordType: String, newRekordType: String)

    /**
     * Adds a new field to an existing rekord type in the schema.
     *
     * @param rekordType the type name of the rekord to modify.
     * @param field the field descriptor including name and type to add.
     * @param defaultValue the value the rekords already stored are given, of the type the field is
     * declared with - a [kotlinx.datetime.LocalDate] for a date field, say. Null gives a field that
     * cannot be null the value of its type that stands for none - zero, false, an empty string, the
     * epoch for a date or a time - and leaves a nullable one without a value.
     * @throws IllegalArgumentException when [defaultValue] is not of a type the field can hold.
     */
    abstract suspend fun addField(
        rekordType: String,
        field: FieldWithType,
        defaultValue: PrimitiveRekordValue? = null,
    )

    /**
     * Removes a field from an existing rekord type in the schema.
     *
     * @param rekordType the type name of the rekord to modify.
     * @param fieldName the name of the field to remove.
     */
    abstract suspend fun removeField(rekordType: String, fieldName: String)

    /**
     * Renames a field within an existing rekord type in the schema.
     *
     * @param rekordType the type name of the rekord to modify.
     * @param oldFieldName the current name of the field.
     * @param newFieldName the new name to assign to the field.
     */
    abstract suspend fun renameField(rekordType: String, oldFieldName: String, newFieldName: String)
}

internal suspend fun RekordsSchemaEditor.init(rekordsEditor: RekordsMigrationEditor) {
    val currentVersion = version()
    val newVersion = schema.version
    when {
        currentVersion == 0 -> {
            schema.rekordTypes.forEach { addRekordType(it) }
            runOnEditor(rekordsEditor) { with(schema) { onCreate() } }
            updateVersion(newVersion)
        }
        currentVersion < newVersion -> {
            runOnEditor(rekordsEditor) { with(schema) { onUpgrade(currentVersion, newVersion) } }
            updateVersion(newVersion)
        }
    }
}

@InternalRekordsApi
fun RekordsSchemaEditor.rekordSchema(rekordType: String): Map<String, FieldWithType> =
    rekordSchemas.getOrPut(rekordType) { schema.rekordSchema(rekordType) }
