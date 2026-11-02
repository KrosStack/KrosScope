package io.github.krosstack.kscope.cli.service.launch

import java.nio.file.Path

internal fun interface ProcessSpawner {
    fun spawn(launch: ServiceLaunch, workingDirectory: Path): SpawnedProcess
}
