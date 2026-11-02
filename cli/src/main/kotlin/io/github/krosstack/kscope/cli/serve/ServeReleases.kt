package io.github.krosstack.kscope.cli.serve

import java.util.concurrent.atomic.AtomicBoolean

internal class ServeReleases(private val enabled: Boolean) {
    private val requested = AtomicBoolean()

    fun request() {
        if (enabled) requested.set(true)
    }

    fun takeRequested(): Boolean = requested.getAndSet(false)
}
