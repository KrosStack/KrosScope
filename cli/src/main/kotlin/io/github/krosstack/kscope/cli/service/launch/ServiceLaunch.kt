package io.github.krosstack.kscope.cli.service.launch

import java.nio.file.Path

internal data class ServiceLaunch(val executable: Path, val arguments: List<String>) {
    val command: List<String> get() = listOf(executable.toString()) + arguments
}
