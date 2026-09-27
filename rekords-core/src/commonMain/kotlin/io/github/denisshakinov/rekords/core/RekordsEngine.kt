@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

/**
 * Creates the [RekordsEditor] a store keeps its rekords with - what a storage implementation
 * offers, the way rekords-sqlite and rekords-indexeddb do.
 *
 * An engine that registers itself is found by [RekordsStore] without being named: the module it
 * comes with is all a target has to depend on. See [RekordsEngineContainer] for how.
 *
 * @param C the configuration the engine is created with, which an engine is free to add to.
 */
interface RekordsEngine<out C : RekordsEngineConfig> {

    /** Creates an editor configured by [block]. */
    fun create(block: C.() -> Unit = {}): RekordsEditor
}

/** What every [RekordsEngine] is configured with. */
open class RekordsEngineConfig {

    /**
     * The name of the storage the rekords are kept in - a database file, an IndexedDB database -
     * which the engine resolves to where the platform keeps such storages.
     */
    var name: String = "rekords"

    /** Receives what the editor reports, such as the statements a SQL editor runs. */
    var logger: RekordsLogger = RekordsLogger.None
}

/**
 * Holds a [RekordsEngine] to be found through `java.util.ServiceLoader`, which is how an engine
 * registers itself on the JVM and Android: the module declares its container in
 * `META-INF/services/io.github.denisshakinov.rekords.core.RekordsEngineContainer`.
 *
 * Elsewhere an engine registers itself through [RekordsEngines.register] as its module is loaded.
 */
@InternalRekordsApi
interface RekordsEngineContainer {
    val engine: RekordsEngine<RekordsEngineConfig>
}

/** The engines registered on targets where no `ServiceLoader` finds them. */
@InternalRekordsApi
object RekordsEngines {

    private val registered: MutableList<RekordsEngine<RekordsEngineConfig>> = mutableListOf()

    /** Registers [engine], to be called as the module it comes with is loaded. */
    fun register(engine: RekordsEngine<RekordsEngineConfig>) {
        if (engine !in registered) {
            registered += engine
        }
    }

    internal fun all(): List<RekordsEngine<RekordsEngineConfig>> =
        (registered + serviceEngines()).distinct()
}

/** The engines a `ServiceLoader` finds, on the targets that have one. */
internal expect fun serviceEngines(): List<RekordsEngine<RekordsEngineConfig>>

/**
 * The one engine among [engines] a store can use without being told which, failing when there is
 * none or more than one - choosing one of several would leave which storage the rekords go to to
 * the order dependencies happen to be loaded in.
 */
internal fun selectEngine(
    engines: List<RekordsEngine<RekordsEngineConfig>>,
): RekordsEngine<RekordsEngineConfig> = when (engines.size) {
    0 -> error(
        "No rekords engine is available for this target. Add the dependency of one to the " +
            "target - rekords-sqlite, rekords-indexeddb - or pass an editor to RekordsStore."
    )
    1 -> engines.single()
    else -> error(
        "Several rekords engines are available for this target: ${engines.joinToString()}. " +
            "Keep one engine dependency for the target, or pass the engine to use, as in " +
            "RekordsStore(schema, ${engines.first()}) { ... }, in the target's own sources."
    )
}
