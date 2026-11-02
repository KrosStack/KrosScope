package io.github.krosstack.kscope.cli.service.home

import com.github.ajalt.clikt.core.CliktCommand
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.service.trust.MachineDirectory
import java.nio.file.Path

internal class ServiceDirectory(val root: Path) {
    val file = ServiceFile(root.resolve("service.toml"))
    val metadataFile: Path = root.resolve("service.json")
    val lockFile: Path = root.resolve("service.lock")
    val logFile: Path = root.resolve("service.log")
    val launchFile: Path = root.resolve("launch.args")
    val registrationFile: Path = root.resolve("run-entry.reg")

    fun runningService(): ServeMetadata? = ServeMetadata.readFrom(metadataFile)?.takeIf(ServeClient::alive)

    companion object {
        const val VARIABLE = MachineDirectory.VARIABLE
        const val PROPERTY = MachineDirectory.PROPERTY

        fun of(command: CliktCommand, property: (String) -> String? = System::getProperty): ServiceDirectory =
            ServiceDirectory(MachineDirectory.resolve(property, { command.currentContext.readEnvvar(it) }))
    }
}
