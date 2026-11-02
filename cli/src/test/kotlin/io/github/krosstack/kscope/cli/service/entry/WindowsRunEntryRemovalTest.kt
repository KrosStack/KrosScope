package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsRunEntryRemovalTest {
    @Test
    fun `uninstall deletes the value and the argument file`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)
        val runner = RecordingRunner()
        val entry = windowsEntry(directory, runner)
        entry.install(sampleLaunch())
        runner.commands.clear()

        val result = entry.uninstall()

        assertEquals(Removal.Removed, result)
        assertEquals(listOf(regQuery(), regDelete()), runner.commands)
        assertTrue(Files.notExists(directory.launchFile))
    }

    @Test
    fun `uninstall when nothing is installed only asks the registry`() = inTemporaryDirectory { home ->
        val runner = RecordingRunner { CommandOutcome.Finished(1, "ERROR: The system was unable to find the key.") }

        val result = windowsEntry(ServiceDirectory(home), runner).uninstall()

        assertEquals(Removal.NotInstalled, result)
        assertEquals(listOf(regQuery()), runner.commands)
    }

    @Test
    fun `uninstall removes an argument file that was left without a value`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)
        Files.writeString(directory.launchFile, "leftover")
        val runner = RecordingRunner { CommandOutcome.Finished(1, "") }

        val result = windowsEntry(directory, runner).uninstall()

        assertEquals(Removal.Removed, result)
        assertEquals(listOf(regQuery()), runner.commands)
        assertTrue(Files.notExists(directory.launchFile))
    }

    @Test
    fun `uninstall keeps the argument file when reg delete fails`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)
        windowsEntry(directory, RecordingRunner()).install(sampleLaunch())
        val runner = failing("delete", outcome = CommandOutcome.Finished(1, "ERROR: Access is denied."))

        val result = windowsEntry(directory, runner).uninstall()

        val refusal = assertIs<Removal.Refused>(result)
        assertTrue(refusal.message.contains("Access is denied."), refusal.message)
        assertTrue(Files.exists(directory.launchFile))
    }

    @Test
    fun `the registered command is read from the output of reg query`() = inTemporaryDirectory { home ->
        val java = "C:\\Program Files\\Java\\javaw.exe"
        val runner = RecordingRunner { CommandOutcome.Finished(0, regQueryOutput(java, home)) }

        val state = windowsEntry(ServiceDirectory(home), runner).state()

        val command = assertNotNull(assertIs<EntryState.Registered>(state).command)
        assertEquals(java, command.java)
        assertEquals(home.toString(), command.argumentFile)
        assertEquals(listOf(regQuery()), runner.commands)
    }

    @Test
    fun `a value that is not the command of an install is registered without a command`() =
        inTemporaryDirectory { home ->
            val output = "\r\n$RUN_KEY\r\n    KrosScope    REG_SZ    C:\\other\\tool.exe\r\n"
            val runner = RecordingRunner { CommandOutcome.Finished(0, output) }

            val state = windowsEntry(ServiceDirectory(home), runner).state()

            assertNull(assertIs<EntryState.Registered>(state).command)
        }

    @Test
    fun `the entry is not registered when reg query fails or cannot run`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)

        val failed = windowsEntry(directory, RecordingRunner { CommandOutcome.Finished(1, "") }).state()
        val unrunnable = windowsEntry(directory, RecordingRunner { CommandOutcome.Unfinished("no file") }).state()

        assertEquals(EntryState.NotRegistered, failed)
        assertEquals(EntryState.NotRegistered, unrunnable)
    }
}
