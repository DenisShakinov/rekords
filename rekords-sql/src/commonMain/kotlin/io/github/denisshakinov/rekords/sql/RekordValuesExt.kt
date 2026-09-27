package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.PrimitiveRekordValues
import io.github.denisshakinov.rekords.core.RekordValue
import io.github.denisshakinov.rekords.core.RekordValues

internal const val SEP = "__"
internal const val LIST_SUFFIX = "[]"

internal fun Iterable<PrimitiveRekordValues>.firstLongValue(): Long? = firstValue() as Long?

internal fun Iterable<PrimitiveRekordValues>.firstIntValue(): Int? = firstLongValue()?.toInt()

internal fun Iterable<PrimitiveRekordValues>.firstStringValue(): String? = firstValue() as String?

internal fun Iterable<PrimitiveRekordValues>.firstValue(): PrimitiveRekordValue? =
    firstOrNull()?.values?.firstOrNull()

internal fun Iterable<PrimitiveRekordValues>.toRekordValues(): List<RekordValues> {
    val rows = toList()
    if (rows.isEmpty()) return emptyList()

    val hasListColumns = rows.first().keys.any { it.substringBefore(SEP).endsWith(LIST_SUFFIX) }

    return if (!hasListColumns) {
        rows.map { it.parseRekordValues() }
    } else {
        buildFromJoinedRows(rows)
    }
}

private fun buildFromJoinedRows(rows: List<PrimitiveRekordValues>): List<RekordValues> {
    val groups =
        LinkedHashMap<Map<String, PrimitiveRekordValue?>, MutableList<PrimitiveRekordValues>>()
    for (row in rows) {
        val rootCols = row.filterKeys { !it.substringBefore(SEP).endsWith(LIST_SUFFIX) }
        val listCols = row.filterKeys { it.substringBefore(SEP).endsWith(LIST_SUFFIX) }
        groups.getOrPut(rootCols) { mutableListOf() }.add(listCols)
    }
    return groups.map { (rootRow, listRows) ->
        val result = rootRow.parseRekordValues().toMutableMap()
        result.putAll(collectCompositeListValues(listRows))
        result
    }
}

private fun collectCompositeListValues(
    rows: List<PrimitiveRekordValues>,
): Map<String, RekordValue.CompositeList?> {
    val items = mutableMapOf<String, MutableList<RekordValue.Composite>>()
    for (row in rows) {
        val byField = mutableMapOf<String, MutableMap<String, PrimitiveRekordValue?>>()
        for ((key, value) in row) {
            val fieldName = key.substringBefore(SEP).removeSuffix(LIST_SUFFIX)
            val rest = key.substringAfter(SEP) // "RekordType__subfield"
            byField.getOrPut(fieldName) { mutableMapOf() }[rest] = value
        }
        for ((fieldName, subCols) in byField) {
            val firstKey = subCols.keys.first()
            val sepIdx = firstKey.indexOf(SEP)
            if (sepIdx == -1) continue
            val rekordType = firstKey.substring(0, sepIdx)
            val leafCols = subCols.mapKeys { (k, _) -> k.substringAfter(SEP) }
            val compositeValues = leafCols.parseRekordValues()
            if (compositeValues.values.all { it == null }) continue // LEFT JOIN null row
            items.getOrPut(fieldName) { mutableListOf() }
                .add(RekordValue.Composite(rekordType, compositeValues))
        }
    }
    return items.mapValues { (_, list) ->
        if (list.isEmpty()) null else RekordValue.CompositeList(
            list
        )
    }
}

private fun PrimitiveRekordValues.parseRekordValues(): RekordValues {
    val result = mutableMapOf<String, RekordValue?>()
    val compositeGroups =
        mutableMapOf<String, Pair<String, MutableMap<String, PrimitiveRekordValue?>>>()

    for ((key, value) in this) {
        val firstSep = key.indexOf(SEP)
        if (firstSep == -1) {
            result[key] = value?.let { RekordValue.Primitive(it) }
            continue
        }
        val fieldName = key.substring(0, firstSep)
        val rest = key.substring(firstSep + SEP.length) // "RekordType__subpath"
        val secondSep = rest.indexOf(SEP)
        if (secondSep == -1) continue // malformed: missing subfield after RekordType
        val rekordType = rest.substring(0, secondSep)
        val subKey = rest.substring(secondSep + SEP.length)
        compositeGroups.getOrPut(fieldName) { rekordType to mutableMapOf() }
            .second[subKey] = value
    }

    for ((fieldName, typeAndSubs) in compositeGroups) {
        val (rekordType, subCols) = typeAndSubs
        result[fieldName] = RekordValue.Composite(rekordType, subCols.parseRekordValues())
    }

    return result
}