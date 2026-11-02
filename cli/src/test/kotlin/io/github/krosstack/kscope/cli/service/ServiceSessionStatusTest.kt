package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.entry.EntryState
import io.github.krosstack.kscope.cli.service.entry.RecordingRunner
import io.github.krosstack.kscope.cli.service.entry.RegisteredCommand
import io.github.krosstack.kscope.cli.service.entry.SessionLine
import io.github.krosstack.kscope.cli.service.entry.SystemdUserEntry
import io.github.krosstack.kscope.cli.service.entry.failing
import io.github.krosstack.kscope.cli.service.host.withTestService
import io.github.krosstack.kscope.cli.service.launch.JvmProcess
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceSessionStatusTest {
    private val notRunning = "The service does not run."

    private fun registered(command: RegisteredCommand): EntryFactory =
        EntryFactory { FakeSessionEntry(EntryState.Registered(command)) }

    private fun status(home: Path, entries: EntryFactory) = runServiceWith(home, listOf("status"), entries)

    @Test
    fun `a service that does not run still says whether it starts with the session`() = inServiceHome { home ->
        val java = Files.createFile(home.resolve("java"))
        val file = Files.writeString(home.resolve("launch.args"), "-cp\n${home.resolve("java")}\n")

        val result = status(home, registered(RegisteredCommand(java.toString(), file.toString())))

        assertEquals(1, result.statusCode, result.output)
        assertEquals(listOf(notRunning, "It starts with your session."), result.stdoutLines())
    }

    @Test
    fun `an entry that names a missing java is reported with what is missing`() = inServiceHome { home ->
        val missing = home.resolve("jdk").resolve("javaw.exe").toString()

        val result = status(home, registered(RegisteredCommand(missing, home.toString())))

        assertEquals(1, result.statusCode, result.output)
        assertEquals(
            listOf(
                notRunning,
                "It is registered to start with your session, but the Java executable $missing does not exist; " +
                    "run `kscope service install` again.",
            ),
            result.stdoutLines(),
        )
    }

    @Test
    fun `a running service shows the session line right after the line about the limits`() = inServiceHome { home ->
        withTestService(home, emptyList()) {
            val result = status(home, EntryFactory { FakeSessionEntry(EntryState.NotRegistered) })

            val lines = result.stdoutLines()
            assertEquals(0, result.statusCode, result.output)
            assertTrue(lines[0].startsWith("The service runs: pid "), lines[0])
            assertTrue(lines[1].startsWith("Limits: "), lines[1])
            assertEquals(SessionLine.NOT_REGISTERED, lines[2])
            assertEquals("It keeps no workspace. Add one with `kscope service add`.", lines[3])
            assertEquals(4, lines.size)
        }
    }

    @Test
    fun `status follows install and a moved java through the entry of the system`() = inServiceHome { home ->
        val config = Files.createDirectory(home.resolve("config"))
        val service = home.resolve("service")
        val java = Files.createFile(home.resolve("java"))
        val library = Files.createFile(home.resolve("lib.jar"))
        val entries = EntryFactory { SystemdUserEntry(it, config, RecordingRunner()) }
        val jvm = JvmProcess(java, library.toString(), emptyList())
        fun run(vararg arguments: String) = runServiceWith(service, arguments.toList(), entries, jvm = jvm)

        val before = run("status").stdoutLines().last()
        run("install")
        val installed = run("status").stdoutLines().last()
        Files.delete(library)
        val moved = run("status").stdoutLines().last()

        assertEquals(SessionLine.NOT_REGISTERED, before)
        assertEquals(SessionLine.STARTS, installed)
        assertEquals(
            "It is registered to start with your session, but the class path entry $library does not exist; " +
                "run `kscope service install` again.",
            moved,
        )
    }

    @Test
    fun `a unit that exists but is not enabled is reported as registered but not enabled`() = inServiceHome { home ->
        val config = Files.createDirectory(home.resolve("config"))
        val service = home.resolve("service")
        runServiceWith(service, listOf("install"), EntryFactory { SystemdUserEntry(it, config, RecordingRunner()) })
        val disabled = EntryFactory { SystemdUserEntry(it, config, failing("is-enabled")) }

        val result = runServiceWith(service, listOf("status"), disabled)

        assertEquals(1, result.statusCode, result.output)
        assertEquals(
            listOf(
                notRunning,
                "It is registered to start with your session, but the unit kscope.service is not enabled; " +
                    "run `kscope service install` again.",
            ),
            result.stdoutLines(),
        )
    }
}
