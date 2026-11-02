package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.launch.LaunchArguments
import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import java.nio.file.Files
import java.nio.file.Path

internal fun <T> inTemporaryDirectory(name: String = "kscope-entry", block: (Path) -> T): T {
    val directory = Files.createTempDirectory(name)
    try {
        return block(directory)
    } finally {
        directory.toFile().deleteRecursively()
    }
}

internal fun sampleLaunch(java: String = "sample/bin/javaw") = ServiceLaunch(
    Path.of(java),
    listOf("-Dfile.encoding=UTF-8", "-cp", "lib/a.jar", "Main", "service", "run", "--log"),
)

internal fun ServiceDirectory.writtenArguments(): List<String> = LaunchArguments.parse(Files.readString(launchFile))

internal fun namesIn(directory: Path): List<String> =
    Files.list(directory).use { files -> files.map { it.fileName.toString() }.sorted().toList() }
