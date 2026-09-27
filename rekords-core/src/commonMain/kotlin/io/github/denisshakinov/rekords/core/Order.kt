package io.github.denisshakinov.rekords.core

sealed class Order(open val field: String) {
    data class Ascending(override val field: String) : Order(field)
    data class Descending(override val field: String) : Order(field)
}