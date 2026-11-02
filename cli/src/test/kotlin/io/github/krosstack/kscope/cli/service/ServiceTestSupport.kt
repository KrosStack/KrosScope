package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.entry.EntryState
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.launch.JvmProcess
import io.github.krosstack.kscope.cli.service.launch.ServiceStarter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal fun <T> inServiceHome(block: (Path) -> T): T {
    val home = Files.createTempDirectory("kscope-service")
    try {
        return block(home)
    } finally {
        home.toFile().deleteRecursively()
    }
}

internal fun <T> inTwoScannedWorkspaces(block: (Path, Path) -> T): T =
    inScannedWorkspace { first -> inScannedWorkspace { second -> block(first, second) } }

internal val SAMPLE_JVM = JvmProcess(
    executable = Path.of("sample-java"),
    classPath = "sample-lib",
    options = listOf("-Dfile.encoding=UTF-8"),
)

internal fun runService(home: Path, vararg arguments: String): CliktCommandTestResult = runServiceWith(
    home,
    arguments.toList(),
    entries = EntryFactory { FakeSessionEntry(EntryState.NotRegistered) },
)

internal fun runServiceWith(
    home: Path,
    arguments: List<String>,
    entries: EntryFactory,
    starter: ServiceStarter = RecordingStarter(),
    jvm: JvmProcess = SAMPLE_JVM,
): CliktCommandTestResult {
    val command = KscopeCommand(serviceCommand = { ServiceCommand(entries, starter) { jvm } })
    val variables = mapOf(ServiceDirectory.VARIABLE to home.toString())
    return command.test(listOf("service") + arguments, envvars = variables) { command.parseWithExitCodes(it) }
}

internal fun CliktCommandTestResult.stdoutLines(): List<String> = stdout.lines().filter { it.isNotBlank() }

internal fun CliktCommandTestResult.workspaceLines(): List<String> = stdoutLines().drop(STATUS_HEADER_LINES)

private const val STATUS_HEADER_LINES = 3

internal fun createDirectoryLink(link: Path, target: Path): Path? = try {
    Files.createSymbolicLink(link, target)
} catch (_: IOException) {
    junction(link, target)
} catch (_: UnsupportedOperationException) {
    null
}

private fun junction(link: Path, target: Path): Path? {
    if (!System.getProperty("os.name").lowercase().contains("windows")) return null
    val process = ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
        .redirectErrorStream(true)
        .start()
    process.inputStream.readAllBytes()
    return link.takeIf { process.waitFor() == 0 }
}
