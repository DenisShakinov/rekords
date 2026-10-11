@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer

/**
 * This filter on a rekord of [rekordType] the way a storage takes it: looking into an embedded
 * class for one of its fields is asking for that field of the rekord, which is how a storage keeps
 * it - see [Embedded]. A type [schema] does not declare is left to be filtered as it is.
 */
@OptIn(InternalSerializationApi::class)
internal fun Filter.flattened(rekordType: String, schema: RekordsSchema): Filter {
    val rekordClass = schema.rekordTypes.firstOrNull { it.rekordType() == rekordType } ?: return this
    return flattened(rekordClass.serializer().descriptor, prefix = "")
}

/** This filter on the fields of [descriptor], named under [prefix] - an embedded class's being. */
private fun Filter.flattened(descriptor: SerialDescriptor, prefix: String): Filter = when (this) {
    is Filter.And -> Filter.And(filters.map { it.flattened(descriptor, prefix) })
    is Filter.Or -> Filter.Or(filters.map { it.flattened(descriptor, prefix) })
    is Filter.Not -> Filter.Not(filter.flattened(descriptor, prefix))
    is FieldFilter -> flattened(descriptor, prefix)
}

@OptIn(ExperimentalSerializationApi::class)
private fun FieldFilter.flattened(descriptor: SerialDescriptor, prefix: String): FieldFilter {
    val index: Int = (0..<descriptor.elementsCount).firstOrNull { index ->
        descriptor.getElementAnnotations(index).filterIsInstance<Field>().firstOrNull()?.name == field
    } ?: return withField(prefix + field)
    if (this !is FieldFilter.Nested) return withField(prefix + field)
    val element: SerialDescriptor = descriptor.getElementDescriptor(index)
    return when {
        element.isEmbedded() -> filter.flattened(element, prefix + descriptor.getField(index).embeddedPrefix())
        // A rekord held, or a list of them, is filtered on its own fields, which are its own names.
        element.kind == StructureKind.LIST ->
            FieldFilter.Nested(prefix + field, filter.flattened(element.getElementDescriptor(0), prefix = ""))
        element.annotations.any { it is Rekord } ->
            FieldFilter.Nested(prefix + field, filter.flattened(element, prefix = ""))
        else -> withField(prefix + field)
    }
}

private fun FieldFilter.withField(name: String): FieldFilter = when (this) {
    is FieldValueFilter.Equals -> copy(field = name)
    is FieldValueFilter.LessThan -> copy(field = name)
    is FieldValueFilter.LessThanOrEquals -> copy(field = name)
    is FieldValueFilter.GreaterThan -> copy(field = name)
    is FieldValueFilter.GreaterThanOrEquals -> copy(field = name)
    is FieldFilter.InList -> copy(field = name)
    is FieldFilter.Contains -> copy(field = name)
    is FieldFilter.Nested -> copy(field = name)
}
