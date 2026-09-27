package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.core.RekordsEditor
import io.github.denisshakinov.rekords.core.RekordsEngine
import io.github.denisshakinov.rekords.core.RekordsEngineConfig

/**
 * The engine keeping rekords in memory, for as long as the editor it creates lives - a test's
 * storage, say:
 * ```kotlin
 * val store = RekordsStore(AppRekordsSchema(), InMemory)
 * ```
 *
 * Nothing is stored anywhere a name could point to, so [RekordsEngineConfig.name] is not read,
 * and nothing is logged. Unlike an engine keeping rekords for good, it does not register itself:
 * a store has to be given it, and a target depending on the module for its tests is left to find
 * its real storage without it.
 */
data object InMemory : RekordsEngine<RekordsEngineConfig> {

    override fun create(block: RekordsEngineConfig.() -> Unit): RekordsEditor = InMemoryRekordsEditor()
}
