package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.service.entry.CommandOutcome
import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.entry.Installation
import io.github.krosstack.kscope.cli.service.entry.RecordingRunner
import io.github.krosstack.kscope.cli.service.entry.SystemEntries
import io.github.krosstack.kscope.cli.service.entry.WindowsRunEntry
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.launch.LaunchArguments
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceInstallTest {
    private val installed = "Installed. The service starts with your session; start it now with `kscope service start`."

    private fun install(home: Path, entry: FakeSessionEntry, starter: RecordingStarter = RecordingStarter()) =
        runServiceWith(home, listOf("install"), EntryFactory { entry }, starter)

    @Test
    fun `install registers the entry with the launch of start, and does not start the service`() =
        inServiceHome { home ->
            ServiceFile(home.resolve("service.toml")).write(emptyList(), ServiceLimits(memoryMib = 512))
            val entry = FakeSessionEntry()
            val starter = RecordingStarter()

            val result = install(home, entry, starter)

            assertEquals(0, result.statusCode, result.output)
            assertEquals(listOf(installed), result.stdoutLines())
            val launch = entry.installed.single()
            assertEquals(SAMPLE_JVM.executable, launch.executable)
            assertTrue("-Xmx512m" in launch.arguments, launch.arguments.toString())
            assertEquals(listOf("service", "run", "--log"), launch.arguments.takeLast(3))
            assertTrue(starter.launches.isEmpty())
        }

    @Test
    fun `a refused install prints the reason on standard error and exits with 1`() = inServiceHome { home ->
        val message = "Windows said no.\nOutput of the program."
        val entry = FakeSessionEntry(installation = { Installation.Refused(message) })

        val result = install(home, entry)

        assertEquals(1, result.statusCode, result.output)
        assertEquals("", result.stdout)
        assertEquals(message, result.stderr.trimEnd())
    }

    @Test
    fun `an error while registering is reported and exits with 1`() = inServiceHome { home ->
        val entry = FakeSessionEntry(installation = { throw IOException("the disk is full") })

        val result = install(home, entry)

        assertEquals(1, result.statusCode, result.output)
        assertEquals("error: the disk is full", result.stderr.trimEnd())
    }

    @Test
    fun `install reports a service toml that cannot be read and registers nothing`() = inServiceHome { home ->
        Files.writeString(home.resolve("service.toml"), "workspaces = 5\n")
        val entry = FakeSessionEntry()

        val result = install(home, entry)

        assertEquals(1, result.statusCode, result.output)
        assertTrue(result.stderr.startsWith("error: "), result.stderr)
        assertTrue(entry.installed.isEmpty())
    }

    @Test
    fun `on a system without an entry install is refused and writes nothing`() = inServiceHome { home ->
        val entries = SystemEntries("Plan 9", home, { null }, RecordingRunner())

        val result = runServiceWith(home, listOf("install"), entries)

        assertEquals(1, result.statusCode, result.output)
        assertEquals(
            "Starting with the session is not supported on Plan 9; use `kscope service start`.",
            result.stderr.trimEnd(),
        )
        assertEquals(emptyList(), Files.list(home).use { it.toList() })
    }

    @Test
    fun `install through the Windows entry writes launch args and runs reg import each time`() = inServiceHome { home ->
        val runner = RecordingRunner { CommandOutcome.Finished(0, "") }
        val entries = EntryFactory { WindowsRunEntry(it, runner, mapOf("SystemRoot" to "C:\\Windows")::get) }

        val first = runServiceWith(home, listOf("install"), entries)
        val second = runServiceWith(home, listOf("install"), entries)

        assertEquals(listOf(installed), first.stdoutLines())
        assertEquals(listOf(installed), second.stdoutLines())
        assertEquals(2, runner.commands.size)
        assertEquals(runner.commands.first(), runner.commands.last())
        assertEquals("import", runner.commands.first()[1])
        val written = LaunchArguments.parse(Files.readString(home.resolve("launch.args")))
        assertTrue("-Dkscope.service.home=$home" in written, written.toString())
        assertTrue(Files.exists(home.resolve("launch.args")))
    }
}
