package io.github.denisshakinov.rekords.indexeddb

import kotlin.js.JsAny

/**
 * Makes `fake-indexeddb` the IndexedDB of the process: Node, which the tests run in, has none of
 * its own. Imported the way each target imports a module, so each declares it for itself.
 */
expect fun installFakeIndexedDB()

/**
 * Sets every class [module] exports as a global, the way a browser has them - the wrapper checks
 * events against them - and `self` to the global object, which the wrapper finds IndexedDB on.
 */
internal fun install(module: JsAny): Unit = js("""(
    Object.keys(module).forEach(function (name) { if (name !== 'default') globalThis[name] = module[name]; }),
    globalThis.self = globalThis,
    undefined
)""")
