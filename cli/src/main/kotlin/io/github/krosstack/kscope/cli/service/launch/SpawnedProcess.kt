package io.github.krosstack.kscope.cli.service.launch

internal interface SpawnedProcess {
    fun awaitExit(millis: Long): Int?

    fun destroy()
}
