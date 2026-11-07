package io.github.krosstack.kscope.cli.service.launch

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class SystemSpawner(
    private val osName: String = System.getProperty("os.name"),
    private val setsid: () -> Path? = {
        System.getenv("PATH").orEmpty().split(File.pathSeparator).asSequence()
            .filter { it.isNotEmpty() }.map { Path.of(it).resolve("setsid").toAbsolutePath() }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
    },
    private val nohup: () -> Path? = {
        System.getenv("PATH").orEmpty().split(File.pathSeparator).asSequence()
            .filter { it.isNotEmpty() }.map { Path.of(it).resolve("nohup").toAbsolutePath() }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
    },
    private val windowsStart: (List<String>, Path) -> Long = { command, directory ->
        WindowsProcessCreation.start(command, directory)
    },
) : ProcessSpawner {
    override fun spawn(launch: ServiceLaunch, workingDirectory: Path): SpawnedProcess {
        val pid = start(launch.command, workingDirectory)
        return RunningProcess(ProcessHandle.of(pid).orElse(null))
    }

    private fun start(command: List<String>, workingDirectory: Path): Long {
        if (osName.startsWith("Windows", ignoreCase = true)) return windowsStart(command, workingDirectory)
        return ProcessBuilder(detachedCommand(command))
            .directory(workingDirectory.toFile())
            .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start().pid()
    }

    internal fun detachedCommand(command: List<String>): List<String> =
        setsid()?.let { listOf(it.toString()) + command }
            ?: (
                listOf("/bin/sh", "-c", "exec \"\$@\" </dev/null >/dev/null 2>&1", "sh") +
                    nohup()?.let { listOf(it.toString()) }.orEmpty() + command
                )

    private class RunningProcess(private val process: ProcessHandle?) : SpawnedProcess {
        override fun awaitExit(millis: Long): Int? = try {
            process?.onExit()?.get(millis, TimeUnit.MILLISECONDS)
            0
        } catch (_: TimeoutException) {
            null
        }

        override fun destroy() {
            process?.destroy()
        }
    }
}
