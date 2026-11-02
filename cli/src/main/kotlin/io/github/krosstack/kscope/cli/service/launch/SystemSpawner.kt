package io.github.krosstack.kscope.cli.service.launch

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal object SystemSpawner : ProcessSpawner {
    private val WINDOWS = System.getProperty("os.name").startsWith("Windows")
    private val HANGUP_GUARDS = listOf("/usr/bin/nohup", "/bin/nohup")

    override fun spawn(launch: ServiceLaunch, workingDirectory: Path): SpawnedProcess {
        val process = ProcessBuilder(guarded(launch.command))
            .directory(workingDirectory.toFile())
            .redirectInput(ProcessBuilder.Redirect.from(File(if (WINDOWS) "NUL" else "/dev/null")))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return RunningProcess(process)
    }

    // Closing the terminal hangs up the processes that were started from it; nohup makes the service ignore that.
    private fun guarded(command: List<String>): List<String> {
        val guard = HANGUP_GUARDS.firstOrNull { !WINDOWS && Files.isExecutable(Path.of(it)) }
        return if (guard == null) command else listOf(guard) + command
    }

    private class RunningProcess(private val process: Process) : SpawnedProcess {
        override fun awaitExit(millis: Long): Int? =
            if (process.waitFor(millis, TimeUnit.MILLISECONDS)) process.exitValue() else null

        override fun destroy() {
            process.destroy()
        }
    }
}
