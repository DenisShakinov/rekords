@file:OptIn(InternalRekordsApi::class)

package io.github.denisshakinov.rekords.core

import java.util.ServiceLoader

internal actual fun serviceEngines(): List<RekordsEngine<RekordsEngineConfig>> =
    ServiceLoader.load(RekordsEngineContainer::class.java, RekordsEngineContainer::class.java.classLoader)
        .map { it.engine }
