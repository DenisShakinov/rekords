package io.github.denisshakinov.rekords.sql

import io.github.denisshakinov.rekords.core.PrimitiveRekordValue
import io.github.denisshakinov.rekords.core.PrimitiveRekordValues

internal fun Iterable<PrimitiveRekordValues>.firstLongValue(): Long? = firstValue() as Long?

internal fun Iterable<PrimitiveRekordValues>.firstIntValue(): Int? = firstLongValue()?.toInt()

internal fun Iterable<PrimitiveRekordValues>.firstStringValue(): String? = firstValue() as String?

internal fun Iterable<PrimitiveRekordValues>.firstValue(): PrimitiveRekordValue? =
    firstOrNull()?.values?.firstOrNull()
