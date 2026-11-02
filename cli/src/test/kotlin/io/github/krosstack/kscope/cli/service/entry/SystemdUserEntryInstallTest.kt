package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SystemdUserEntryInstallTest {
    @Test
    fun `install writes the unit, reloads systemd and enables the unit, in that order`() =
        inTemporaryDirectory("with space") { home ->
            val directory = ServiceDirectory(home.resolve("service dir"))
            val configHome = home.resolve("config")
            val runner = RecordingRunner()
            val launch = sampleLaunch("opt/my java/bin/java")

            val result = systemdEntry(directory, configHome, runner).install(launch)

            assertEquals(Installation.Installed, result)
            assertEquals(listOf(SYSTEMCTL_RELOAD, SYSTEMCTL_ENABLE), runner.commands)
            val text = Files.readString(unitFile(configHome))
            val unit = text.lines()
            assertEquals(listOf("[Unit]", "[Service]", "[Install]"), unit.filter { it.startsWith("[") })
            assertTrue(unit.contains("WantedBy=default.target"), text)
            assertTrue(unit.any { it.startsWith("Description=") }, text)
            val start = SystemdUnit.registeredCommand(text)
            assertEquals(launch.executable.toString(), start?.java)
            assertEquals(directory.launchFile.toString(), start?.argumentFile)
            assertEquals(launch.arguments, directory.writtenArguments())
        }

    @Test
    fun `a second install replaces the unit`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home.resolve("service"))
        val runner = RecordingRunner()
        val entry = systemdEntry(directory, home, runner)
        entry.install(sampleLaunch("first/java"))

        val result = entry.install(sampleLaunch("second/java"))

        assertEquals(Installation.Installed, result)
        assertEquals(listOf(SYSTEMCTL_RELOAD, SYSTEMCTL_ENABLE, SYSTEMCTL_RELOAD, SYSTEMCTL_ENABLE), runner.commands)
        val text = Files.readString(unitFile(home))
        assertTrue(text.contains("second"), text)
        assertEquals(1, Regex("ExecStart=").findAll(text).count())
    }

    @Test
    fun `without a user manager the unit and the argument file are removed and the install is refused`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home.resolve("service"))
            val runner = failing("enable", outcome = CommandOutcome.Finished(1, "Failed to connect to bus"))

            val result = systemdEntry(directory, home, runner).install(sampleLaunch())

            val refusal = assertIs<Installation.Refused>(result)
            assertEquals(NO_USER_MANAGER, refusal.message.lines().first())
            assertTrue(refusal.message.contains("Failed to connect to bus"), refusal.message)
            assertEquals(listOf(SYSTEMCTL_RELOAD, SYSTEMCTL_ENABLE), runner.commands)
            assertTrue(Files.notExists(unitFile(home)))
            assertTrue(Files.notExists(directory.launchFile))
        }

    @Test
    fun `a failing daemon-reload stops the install before the unit is enabled`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home.resolve("service"))
        val runner = failing("daemon-reload")

        val result = systemdEntry(directory, home, runner).install(sampleLaunch())

        assertEquals(NO_USER_MANAGER, assertIs<Installation.Refused>(result).message.lines().first())
        assertEquals(listOf(SYSTEMCTL_RELOAD), runner.commands)
        assertTrue(Files.notExists(unitFile(home)))
    }

    @Test
    fun `a systemctl that times out or cannot run is refused the same way`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home.resolve("service"))

        val outcomes = listOf(CommandOutcome.Unfinished("timed out after 30 seconds"), CommandOutcome.Unfinished("no"))
        for (outcome in outcomes) {
            val result = systemdEntry(directory, home, failing("enable", outcome = outcome)).install(sampleLaunch())

            val refusal = assertIs<Installation.Refused>(result)
            assertEquals(NO_USER_MANAGER, refusal.message.lines().first())
            assertTrue(refusal.message.contains(outcome.reason), refusal.message)
            assertTrue(Files.notExists(unitFile(home)))
        }
    }

    @Test
    fun `a reinstall that fails restores the previous unit and keeps the previous argument file`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home.resolve("service"))
            systemdEntry(directory, home, RecordingRunner()).install(sampleLaunch("first/java"))
            val unitBefore = Files.readString(unitFile(home))
            val argumentsBefore = Files.readString(directory.launchFile)
            val moved = sampleLaunch("second/java").copy(arguments = listOf("-cp", "moved"))

            val result = systemdEntry(directory, home, failing("enable")).install(moved)

            assertIs<Installation.Refused>(result)
            assertEquals(unitBefore, Files.readString(unitFile(home)))
            assertEquals(argumentsBefore, Files.readString(directory.launchFile))
            assertEquals(listOf("launch.args"), namesIn(directory.root))
        }

    @Test
    fun `the unit quotes what systemd would otherwise read as specifiers or variables`() {
        val text = SystemdUnit.text(listOf("/opt/100%/j\"ava", "@/home/a b/\$HOME/launch.args"))

        val start = text.lines().single { it.startsWith("ExecStart=") }
        assertEquals("ExecStart=\"/opt/100%%/j\\\"ava\" \"@/home/a b/\$\$HOME/launch.args\"", start)
        val read = SystemdUnit.registeredCommand(text)
        assertEquals("/opt/100%/j\"ava", read?.java)
        assertEquals("/home/a b/\$HOME/launch.args", read?.argumentFile)
    }
}
