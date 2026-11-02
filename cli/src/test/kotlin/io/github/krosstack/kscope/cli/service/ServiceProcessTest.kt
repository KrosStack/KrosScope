package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceProcessTest {
    private val windows = System.getProperty("os.name").lowercase().contains("windows")

    private fun launcher(): Path {
        val installed = Path.of(checkNotNull(System.getProperty("kscope.distribution")))
        return installed.resolve("bin").resolve(if (windows) "kscope.bat" else "kscope")
    }

    private fun command(home: Path, vararg arguments: String): ProcessBuilder {
        val builder = ProcessBuilder(listOf(launcher().toString(), "service") + arguments.toList())
        builder.environment()["JAVA_HOME"] = System.getProperty("java.home")
        builder.environment()[ServiceDirectory.VARIABLE] = home.toString()
        builder.environment().remove("KSCOPE_NO_SERVER")
        return builder.redirectErrorStream(true)
    }

    private fun finish(process: Process, seconds: Long) =
        assertTrue(process.waitFor(seconds, TimeUnit.SECONDS), "a kscope process did not end")

    private fun awaitFile(watcher: WatchService, file: Path, service: Process) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (!Files.exists(file)) {
            check(service.isAlive) { "the service ended before it wrote $file" }
            check(System.nanoTime() < deadline) { "$file never appeared" }
            watcher.poll(1, TimeUnit.SECONDS)?.let { key ->
                key.pollEvents()
                key.reset()
            }
        }
    }

    @Test
    fun `the distribution runs the service until stop ends it`() = inServiceHome { home ->
        val diagnostics = Files.createTempFile("kscope-service", ".txt")
        home.fileSystem.newWatchService().use { watcher ->
            home.register(watcher, StandardWatchEventKinds.ENTRY_CREATE)
            val service = command(home, "run").redirectOutput(diagnostics.toFile()).start()
            try {
                awaitFile(watcher, home.resolve("service.json"), service)

                val status = command(home, "status").start()
                val statusText = status.inputStream.readAllBytes().decodeToString()
                finish(status, WAIT_SECONDS)
                val stop = command(home, "stop").start()
                val stopText = stop.inputStream.readAllBytes().decodeToString()
                finish(stop, WAIT_SECONDS)
                finish(service, WAIT_SECONDS)

                assertEquals(0, status.exitValue(), statusText)
                assertTrue(statusText.contains("The service runs: pid "), statusText)
                assertTrue(statusText.contains("It keeps no workspace."), statusText)
                assertEquals(0, stop.exitValue(), stopText)
                assertTrue(stopText.contains("The service stopped."), stopText)
                assertEquals(0, service.exitValue())
                val started = "The service runs. Stop it with Ctrl+C or `kscope service stop`."
                assertTrue(Files.readString(diagnostics).contains(started))
                assertTrue(Files.notExists(home.resolve("service.json")))
            } finally {
                service.descendants().use { children -> children.forEach { it.destroyForcibly() } }
                if (service.isAlive) service.destroyForcibly()
                Files.deleteIfExists(diagnostics)
            }
        }
    }

    private companion object {
        const val WAIT_SECONDS = 120L
    }
}
