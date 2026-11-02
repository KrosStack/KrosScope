package io.github.krosstack.kscope.cli.service

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import io.github.krosstack.kscope.cli.service.host.withTestService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceRunLogTest {
    private val timestamp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"

    private fun awaitFile(home: Path, name: String, running: Future<*>) {
        home.fileSystem.newWatchService().use { watcher ->
            home.register(watcher, StandardWatchEventKinds.ENTRY_CREATE)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
            while (!Files.exists(home.resolve(name))) {
                check(!running.isDone) { "the service ended before it wrote $name" }
                check(System.nanoTime() < deadline) { "$name never appeared" }
                watcher.poll(1, TimeUnit.SECONDS)?.let { key ->
                    key.pollEvents()
                    key.reset()
                }
            }
        }
    }

    @Test
    fun `run with log writes its lines with the time to service log and nothing to standard error`() =
        inServiceHome { home ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val run: Future<CliktCommandTestResult> = executor.submit<CliktCommandTestResult> {
                    runService(home, "run", "--log")
                }
                awaitFile(home, "service.json", run)

                val stop = runService(home, "stop")
                val result = run.get(WAIT_SECONDS, TimeUnit.SECONDS)

                assertEquals(listOf("The service stopped."), stop.stdoutLines())
                assertEquals(0, result.statusCode, result.output)
                assertEquals("", result.stderr)
                val lines = Files.readAllLines(home.resolve("service.log"))
                val started = Regex("$timestamp The service runs\\. Stop it with Ctrl\\+C or `kscope service stop`\\.")
                assertTrue(started.matches(lines.first()), lines.toString())
            } finally {
                executor.shutdownNow()
            }
        }

    @Test
    fun `a second run with log is refused in the log and does not start the file anew`() = inServiceHome { home ->
        withTestService(home, emptyList()) {
            Files.writeString(home.resolve("service.log"), "written by the running service\n")

            val result = runService(home, "run", "--log")

            assertEquals(1, result.statusCode, result.output)
            assertEquals("", result.stderr)
            val lines = Files.readAllLines(home.resolve("service.log"))
            assertEquals("written by the running service", lines.first())
            assertTrue(Regex("$timestamp The service runs already\\.").matches(lines.last()), lines.toString())
            assertEquals(2, lines.size)
        }
    }

    @Test
    fun `a second run without log is refused on standard error as before`() = inServiceHome { home ->
        withTestService(home, emptyList()) {
            val result = runService(home, "run")

            assertEquals(1, result.statusCode, result.output)
            assertEquals("The service runs already.", result.stderr.trimEnd())
            assertTrue(Files.notExists(home.resolve("service.log")))
        }
    }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
