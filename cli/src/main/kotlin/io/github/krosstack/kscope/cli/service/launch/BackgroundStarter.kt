package io.github.krosstack.kscope.cli.service.launch

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

internal class BackgroundStarter(
    private val spawner: ProcessSpawner = SystemSpawner,
    private val timeoutNanos: Long = TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS),
    private val clock: () -> Long = System::nanoTime,
) : ServiceStarter {
    override fun start(directory: ServiceDirectory, launch: ServiceLaunch): StartOutcome {
        val process = try {
            Files.createDirectories(directory.root)
            spawner.spawn(launch, directory.root)
        } catch (failure: IOException) {
            return StartOutcome.NotStarted(failure.message.orEmpty())
        }
        return awaitService(directory, process).also { if (it is StartOutcome.NotStarted) process.destroy() }
    }

    private fun awaitService(directory: ServiceDirectory, process: SpawnedProcess): StartOutcome {
        val begin = clock()
        var exited = false
        while (!exited && clock() - begin < timeoutNanos) {
            directory.runningService()?.let { return StartOutcome.Started(it.pid) }
            exited = process.awaitExit(POLL_MILLIS) != null
        }
        return directory.runningService()?.let { StartOutcome.Started(it.pid) } ?: StartOutcome.NotStarted()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
        const val POLL_MILLIS = 100L
    }
}
