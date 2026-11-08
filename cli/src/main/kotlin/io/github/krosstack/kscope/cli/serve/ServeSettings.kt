package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.serve.watch.WatchSettings

internal data class ServeSettings(
    val idleTimeoutMillis: Long = 0,
    val clock: () -> Long = System::nanoTime,
    val requestTimeoutMillis: Int = ServeWire.REQUEST_TIMEOUT_MILLIS,
    val onRequest: (ServeRequest) -> Unit = {},
    val onConnection: () -> Unit = {},
    val watchEnabled: Boolean = false,
    val mapEnabled: Boolean = false,
    val watch: WatchSettings = WatchSettings(),
    val hosted: Boolean = false,
    val onLoadChange: (Boolean) -> Unit = {},
    val beforeExitResponse: () -> Unit = {},
)
