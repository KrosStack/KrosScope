package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WindowsRunEntryReinstallTest {
    @Test
    fun `a first install that fails leaves nothing behind`() = inTemporaryDirectory { home ->
        val runner = RecordingRunner { CommandOutcome.Finished(1, "ERROR: Access is denied.") }

        val result = windowsEntry(ServiceDirectory(home), runner).install(sampleLaunch())

        assertIs<Installation.Refused>(result)
        assertEquals(emptyList(), namesIn(home))
    }

    @Test
    fun `a reinstall that fails leaves the previous argument file and the previous value as they were`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home)
            windowsEntry(directory, RecordingRunner()).install(sampleLaunch("C:\\Java\\bin\\javaw.exe"))
            val failing = RecordingRunner { CommandOutcome.Finished(1, "ERROR: Access is denied.") }
            val moved = sampleLaunch("D:\\Moved\\javaw.exe").copy(arguments = listOf("-cp", "moved"))

            val result = windowsEntry(directory, failing).install(moved)

            assertIs<Installation.Refused>(result)
            assertEquals(sampleLaunch().arguments, directory.writtenArguments())
            assertEquals(listOf(regImport(directory)), failing.commands)
            assertEquals(listOf("launch.args"), namesIn(home))
        }

    @Test
    fun `a reinstall that succeeds replaces the argument file`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)
        windowsEntry(directory, RecordingRunner()).install(sampleLaunch("C:\\Java\\bin\\javaw.exe"))
        val moved = sampleLaunch("D:\\Moved\\javaw.exe").copy(arguments = listOf("-cp", "moved"))

        val result = windowsEntry(directory, RecordingRunner()).install(moved)

        assertEquals(Installation.Installed, result)
        assertEquals(listOf("-cp", "moved"), directory.writtenArguments())
        assertEquals(listOf("launch.args"), namesIn(home))
    }
}
