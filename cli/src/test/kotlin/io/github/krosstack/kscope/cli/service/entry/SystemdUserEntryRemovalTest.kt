package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SystemdUserEntryRemovalTest {
    @Test
    fun `uninstall disables the unit, deletes both files and reloads systemd, in that order`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home.resolve("service"))
            val runner = RecordingRunner()
            val entry = systemdEntry(directory, home, runner)
            entry.install(sampleLaunch())
            runner.commands.clear()

            val result = entry.uninstall()

            assertEquals(Removal.Removed, result)
            assertEquals(listOf(SYSTEMCTL_DISABLE, SYSTEMCTL_RELOAD), runner.commands)
            assertTrue(Files.notExists(unitFile(home)))
            assertTrue(Files.notExists(directory.launchFile))
        }

    @Test
    fun `uninstall when nothing is installed runs nothing`() = inTemporaryDirectory { home ->
        val runner = RecordingRunner()

        val result = systemdEntry(ServiceDirectory(home.resolve("service")), home, runner).uninstall()

        assertEquals(Removal.NotInstalled, result)
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun `uninstall removes an argument file that was left without a unit and runs nothing`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home)
            Files.writeString(directory.launchFile, "leftover")
            val runner = RecordingRunner()

            val result = systemdEntry(directory, home.resolve("config"), runner).uninstall()

            assertEquals(Removal.Removed, result)
            assertTrue(runner.commands.isEmpty())
            assertTrue(Files.notExists(directory.launchFile))
        }

    @Test
    fun `uninstall keeps the unit when systemctl disable fails, so that it can be tried again`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home.resolve("service"))
            systemdEntry(directory, home, RecordingRunner()).install(sampleLaunch())
            val runner = failing("disable", outcome = CommandOutcome.Finished(1, "Failed to connect to bus"))

            val result = systemdEntry(directory, home, runner).uninstall()

            val refusal = assertIs<Removal.Refused>(result)
            assertTrue(refusal.message.contains("Failed to connect to bus"), refusal.message)
            assertEquals(listOf(SYSTEMCTL_DISABLE), runner.commands)
            assertTrue(Files.exists(unitFile(home)))
            assertTrue(Files.exists(directory.launchFile))
        }

    @Test
    fun `uninstall reports a reload that fails after the files are gone`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home.resolve("service"))
        systemdEntry(directory, home, RecordingRunner()).install(sampleLaunch())
        val runner = failing("daemon-reload", outcome = CommandOutcome.Unfinished("timed out after 30 seconds"))

        val result = systemdEntry(directory, home, runner).uninstall()

        assertTrue(assertIs<Removal.Refused>(result).message.contains("timed out after 30 seconds"))
        assertTrue(Files.notExists(unitFile(home)))
        assertTrue(Files.notExists(directory.launchFile))
    }

    @Test
    fun `the entry is registered when the unit file exists and is enabled, and not otherwise`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home.resolve("service"))
            val runner = RecordingRunner()
            val entry = systemdEntry(directory, home, runner)
            val before = entry.state()
            val commandsBefore = runner.commands.size

            entry.install(sampleLaunch("opt/java"))
            runner.commands.clear()
            val command = assertNotNull(assertIs<EntryState.Registered>(entry.state()).command)

            assertEquals(EntryState.NotRegistered, before)
            assertEquals(0, commandsBefore)
            assertEquals(listOf(SYSTEMCTL_IS_ENABLED), runner.commands)
            assertEquals(sampleLaunch("opt/java").executable.toString(), command.java)
            assertEquals(directory.launchFile.toString(), command.argumentFile)
        }

    @Test
    fun `a unit file that is not enabled is reported as disabled, also when systemctl fails or cannot run`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home.resolve("service"))
            systemdEntry(directory, home, RecordingRunner()).install(sampleLaunch())
            val answers = listOf(
                CommandOutcome.Finished(1, "disabled"),
                CommandOutcome.Finished(4, "Failed to connect to bus"),
                CommandOutcome.Unfinished("timed out after 30 seconds"),
                CommandOutcome.Unfinished("Cannot run program \"systemctl\""),
            )

            for (answer in answers) {
                val state = systemdEntry(directory, home, RecordingRunner { answer }).state()

                assertEquals(EntryState.Disabled("kscope.service"), state, answer.toString())
            }
        }
}
