package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceBackgroundProcessTest {
    private class Finished(val exitCode: Int, val text: String)

    private val windows = System.getProperty("os.name").lowercase().contains("windows")

    private fun launcher(): Path {
        val installed = Path.of(checkNotNull(System.getProperty("kscope.distribution")))
        return installed.resolve("bin").resolve(if (windows) "kscope.bat" else "kscope")
    }

    private fun kscope(home: Path, vararg arguments: String): Finished {
        val output = Files.createTempFile("kscope-service-output", ".txt")
        val builder = ProcessBuilder(listOf(launcher().toString(), "service") + arguments.toList())
        builder.environment()["JAVA_HOME"] = System.getProperty("java.home")
        builder.environment()[ServiceDirectory.VARIABLE] = home.toString()
        builder.environment().remove("KSCOPE_NO_SERVER")
        val process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            assertTrue(process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS), "kscope service ${arguments[0]} did not end")
            return Finished(process.exitValue(), Files.readString(output))
        } finally {
            process.destroyForcibly()
            Files.deleteIfExists(output)
        }
    }

    private fun endBackgroundService(home: Path) {
        val pid = ServeMetadata.readFrom(home.resolve("service.json"))?.pid ?: return
        ProcessHandle.of(pid).ifPresent { handle ->
            handle.destroyForcibly()
            handle.onExit().get(WAIT_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `the distribution starts the service in the background and stop ends it`() = inServiceHome { home ->
        try {
            val start = kscope(home, "start")
            assertEquals(0, start.exitCode, start.text)
            val pid = checkNotNull(Regex("pid (\\d+)").find(start.text)) { start.text }.groupValues[1].toLong()
            val handle = ProcessHandle.of(pid).orElseThrow()
            val program = handle.info().command().orElse("")
            assertEquals("The service runs: pid $pid.", start.text.trim())

            val status = kscope(home, "status")
            val again = kscope(home, "start")
            val stop = kscope(home, "stop")
            handle.onExit().get(WAIT_SECONDS, TimeUnit.SECONDS)

            assertEquals(0, status.exitCode, status.text)
            assertTrue(status.text.contains("The service runs: pid $pid, version "), status.text)
            assertEquals(0, again.exitCode, again.text)
            assertEquals("The service runs already: pid $pid.", again.text.trim())
            assertEquals(0, stop.exitCode, stop.text)
            assertTrue(Path.of(program).fileName.toString().startsWith(if (windows) "javaw" else "java"), program)
            assertTrue(Files.notExists(home.resolve("service.json")))
            val log = Files.readString(home.resolve("service.log"))
            assertTrue(log.contains("The service runs. Stop it with Ctrl+C or `kscope service stop`."), log)
        } finally {
            endBackgroundService(home)
        }
    }

    private companion object {
        const val WAIT_SECONDS = 120L
    }
}
