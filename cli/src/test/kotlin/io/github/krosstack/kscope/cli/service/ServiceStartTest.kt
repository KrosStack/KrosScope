package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.host.withTestService
import io.github.krosstack.kscope.cli.service.launch.StartOutcome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceStartTest {
    private fun start(home: Path, starter: RecordingStarter) =
        runServiceWith(home, listOf("start"), EntryFactory { FakeSessionEntry() }, starter)

    @Test
    fun `start launches the service with the memory ceiling of service toml and prints its pid`() =
        inServiceHome { home ->
            ServiceFile(home.resolve("service.toml")).write(emptyList(), ServiceLimits(memoryMib = 1024))
            val starter = RecordingStarter(StartOutcome.Started(4711))

            val result = start(home, starter)

            assertEquals(0, result.statusCode, result.output)
            assertEquals(listOf("The service runs: pid 4711."), result.stdoutLines())
            val launch = starter.launches.single()
            assertEquals(SAMPLE_JVM.executable, launch.executable)
            assertTrue("-Xmx1024m" in launch.arguments, launch.arguments.toString())
            assertTrue("-Dkscope.service.home=$home" in launch.arguments, launch.arguments.toString())
            assertEquals(listOf("service", "run", "--log"), launch.arguments.takeLast(3))
            assertEquals(home, starter.directories.single().root)
        }

    @Test
    fun `start says that the service runs already and starts nothing`() = inServiceHome { home ->
        withTestService(home, emptyList()) {
            val starter = RecordingStarter(StartOutcome.Started(1))

            val result = start(home, starter)

            assertEquals(0, result.statusCode, result.output)
            val pid = ProcessHandle.current().pid()
            assertEquals(listOf("The service runs already: pid $pid."), result.stdoutLines())
            assertTrue(starter.launches.isEmpty())
        }
    }

    @Test
    fun `start that does not bring the service up names the log and exits with 1`() = inServiceHome { home ->
        val result = start(home, RecordingStarter(StartOutcome.NotStarted()))

        assertEquals(1, result.statusCode, result.output)
        assertEquals("", result.stdout)
        assertEquals("The service did not start; see ${home.resolve("service.log")}.", result.stderr.trimEnd())
    }

    @Test
    fun `start shows why a process could not be started`() = inServiceHome { home ->
        val result = start(home, RecordingStarter(StartOutcome.NotStarted("no such program")))

        assertEquals(1, result.statusCode, result.output)
        assertEquals(
            listOf("error: no such program", "The service did not start; see ${home.resolve("service.log")}."),
            result.stderr.lines().filter { it.isNotBlank() },
        )
    }

    @Test
    fun `start reports a service toml that cannot be read and starts nothing`() = inServiceHome { home ->
        Files.writeString(home.resolve("service.toml"), "workspaces = 5\n")
        val starter = RecordingStarter(StartOutcome.Started(1))

        val result = start(home, starter)

        assertEquals(1, result.statusCode, result.output)
        assertTrue(result.stderr.startsWith("error: "), result.stderr)
        assertTrue(starter.launches.isEmpty())
    }
}
