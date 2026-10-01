@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlin.coroutines.RestrictsSuspension
import kotlin.reflect.KClass

/**
 * The editor a schema is created and upgraded on, in [RekordsSchema.onCreate] and
 * [RekordsSchema.onUpgrade]: the rekords operations of a [RekordsEditor], together with the
 * changes to the storage the rekords are kept in.
 *
 * A migration runs in a transaction of its own, so a failing one leaves the storage the way it
 * was, at the version it was. See [RekordsSchemaEditor] for what each change does.
 */
@RestrictsSuspension
interface RekordsMigrationEditor : RekordsEditor {

    /** See [RekordsSchemaEditor.addRekordType]. */
    suspend fun addRekordType(rekordClass: KClass<*>)

    /** See [RekordsSchemaEditor.removeRekordType]. */
    suspend fun removeRekordType(rekordType: String)

    /** See [RekordsSchemaEditor.renameRekordType]. */
    suspend fun renameRekordType(oldRekordType: String, newRekordType: String)

    /** See [RekordsSchemaEditor.addField]. */
    suspend fun addField(
        rekordType: String,
        field: FieldWithType,
        defaultValue: PrimitiveRekordValue? = null,
    )

    /** See [RekordsSchemaEditor.removeField]. */
    suspend fun removeField(rekordType: String, fieldName: String)

    /** See [RekordsSchemaEditor.renameField]. */
    suspend fun renameField(rekordType: String, oldFieldName: String, newFieldName: String)
}

suspend inline fun <reified R> RekordsMigrationEditor.addRekordType() =
    addRekordType(R::class)

suspend inline fun <reified R> RekordsMigrationEditor.removeRekordType() =
    removeRekordType(rekordType<R>())

suspend inline fun <reified R> RekordsMigrationEditor.renameRekordType(oldRekordType: String) =
    renameRekordType(oldRekordType, rekordType<R>())

suspend inline fun <reified R> RekordsMigrationEditor.addField(
    fieldName: String,
    defaultValue: PrimitiveRekordValue? = null,
) = addField(rekordType<R>(), allFields<R>().first { it.field.name == fieldName }, defaultValue)

suspend inline fun <reified R> RekordsMigrationEditor.removeField(fieldName: String) =
    removeField(rekordType<R>(), fieldName)

suspend inline fun <reified R> RekordsMigrationEditor.renameField(
    oldFieldName: String,
    newFieldName: String
) = renameField(rekordType<R>(), oldFieldName, newFieldName)

/**
 * The migration editor over [editor], the one a transaction runs on: the changes to the storage go
 * to its schema editor, and everything else to it.
 */
internal class SchemaMigrationEditor(
    private val editor: RekordsEditor,
) : RekordsMigrationEditor, RekordsEditor by editor {

    override suspend fun addRekordType(rekordClass: KClass<*>) =
        editor.schemaEditor.addRekordType(rekordClass)

    override suspend fun removeRekordType(rekordType: String) =
        editor.schemaEditor.removeRekordType(rekordType)

    override suspend fun renameRekordType(oldRekordType: String, newRekordType: String) =
        editor.schemaEditor.renameRekordType(oldRekordType, newRekordType)

    override suspend fun addField(
        rekordType: String,
        field: FieldWithType,
        defaultValue: PrimitiveRekordValue?,
    ) = editor.schemaEditor.addField(rekordType, field, editor.storedDefaultValue(field, defaultValue))

    override suspend fun removeField(rekordType: String, fieldName: String) =
        editor.schemaEditor.removeField(rekordType, fieldName)

    override suspend fun renameField(rekordType: String, oldFieldName: String, newFieldName: String) =
        editor.schemaEditor.renameField(rekordType, oldFieldName, newFieldName)

    /** Joins the transaction the migration runs in, as the store's editors do. */
    override suspend fun <T> transaction(action: suspend RekordsEditor.() -> T): T =
        runOnEditor(this, action)
}
