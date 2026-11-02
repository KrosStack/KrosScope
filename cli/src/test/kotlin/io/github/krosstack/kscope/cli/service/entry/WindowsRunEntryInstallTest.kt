package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WindowsRunEntryInstallTest {
    @Test
    fun `install writes the argument file and imports one registration file, which is gone afterwards`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home)
            val imported = mutableListOf<ByteArray>()
            val runner = runnerReadingImports(imported)

            val result = windowsEntry(directory, runner).install(sampleLaunch("C:\\Java\\bin\\javaw.exe"))

            assertEquals(Installation.Installed, result)
            assertEquals(listOf(regImport(directory)), runner.commands)
            val commandLine = commandLineOf("C:\\Java\\bin\\javaw.exe", directory)
            assertEquals(commandLine, valueOfRegistrationFile(imported.single()))
            assertEquals(sampleLaunch().arguments, directory.writtenArguments())
            assertTrue(Files.notExists(directory.registrationFile))
        }

    @Test
    fun `the registration file has exactly these bytes for a command line with spaces, backslashes and quotes`() {
        val commandLine = "\"C:\\Program Files\\Java\\javaw.exe\" \"@C:\\Users\\me\\service dir\\launch.args\""

        val bytes = RegistryValue.registrationFile(commandLine)

        val expected = "\uFEFFWindows Registry Editor Version 5.00\r\n" +
            "\r\n" +
            "[HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run]\r\n" +
            "\"KrosScope\"=\"\\\"C:\\\\Program Files\\\\Java\\\\javaw.exe\\\" " +
            "\\\"@C:\\\\Users\\\\me\\\\service dir\\\\launch.args\\\"\"\r\n" +
            "\r\n"
        assertContentEquals(expected.toByteArray(Charsets.UTF_16LE), bytes)
        assertEquals(listOf(0xFF.toByte(), 0xFE.toByte()), bytes.take(2))
    }

    @Test
    fun `the value read back from the file is the command line, whatever it holds`() {
        val commandLines = listOf(
            "\"C:\\Program Files\\Java\\javaw.exe\" \"@C:\\a b\\launch.args\"",
            "\"C:\\ends with a backslash\\\" \"@x\"",
            "\"say \\\"hi\\\"\" \"@\\\\server\\share\\launch.args\"",
        )

        for (commandLine in commandLines) {
            assertEquals(commandLine, valueOfRegistrationFile(RegistryValue.registrationFile(commandLine)), commandLine)
        }
    }

    @Test
    fun `characters outside the system code page survive because the file is UTF-16`() {
        val commandLine = "\"C:\\Users\\Jos\u00e9\\\u65e5\u672c\\javaw.exe\" \"@C:\\Users\\Jos\u00e9\\launch.args\""

        val bytes = RegistryValue.registrationFile(commandLine)

        assertEquals(commandLine, valueOfRegistrationFile(bytes))
        val accent = "Jos\u00e9".toByteArray(Charsets.UTF_16LE).toList()
        assertTrue(bytes.toList().windowed(accent.size).any { it == accent })
    }

    @Test
    fun `a path with such characters is written to the file as it is`() = inTemporaryDirectory { home ->
        val java = "C:\\Jos\u00e9\\\u65e5\u672c\\javaw.exe"
        val usable = runCatching { Path.of(java) }.isSuccess
        assumeTrue(usable, "this file system cannot name the path")
        val directory = ServiceDirectory(home)
        val imported = mutableListOf<ByteArray>()

        windowsEntry(directory, runnerReadingImports(imported)).install(sampleLaunch(java))

        assertEquals(commandLineOf(java, directory), valueOfRegistrationFile(imported.single()))
    }

    @Test
    fun `a command line of 260 characters is accepted and one of 261 is refused`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)
        val runner = RecordingRunner()
        val atLimit = "J".repeat(260 - commandLineOf("", directory).length)

        val accepted = windowsEntry(directory, runner).install(sampleLaunch(atLimit))
        val refused = windowsEntry(directory, runner).install(sampleLaunch(atLimit + "J"))

        assertEquals(Installation.Installed, accepted)
        assertIs<Installation.Refused>(refused)
        assertEquals(1, runner.commands.size)
    }

    @Test
    fun `a command line that is too long names both paths, registers nothing and writes nothing`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home)
            val runner = RecordingRunner()
            val java = "C:\\" + "deep\\".repeat(60) + "javaw.exe"

            val result = windowsEntry(directory, runner).install(sampleLaunch(java))

            val refusal = assertIs<Installation.Refused>(result)
            assertTrue(refusal.message.contains("260"), refusal.message)
            assertTrue(refusal.message.contains(Path.of(java).toString()), refusal.message)
            assertTrue(refusal.message.contains(directory.launchFile.toString()), refusal.message)
            assertTrue(runner.commands.isEmpty())
            assertEquals(emptyList(), Files.list(home).use { it.toList() })
        }

    @Test
    fun `a failing import is refused with its output and leaves no file`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)
        val runner = RecordingRunner { CommandOutcome.Finished(1, "ERROR: Access is denied.\r\n") }

        val result = windowsEntry(directory, runner).install(sampleLaunch())

        val refusal = assertIs<Installation.Refused>(result)
        assertTrue(refusal.message.contains("reg.exe import"), refusal.message)
        assertTrue(refusal.message.contains("exit code 1"), refusal.message)
        assertTrue(refusal.message.contains("ERROR: Access is denied."), refusal.message)
        assertEquals(emptyList(), Files.list(home).use { it.toList() })
    }

    @Test
    fun `an import that times out is refused and leaves no file`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)
        val runner = RecordingRunner { CommandOutcome.Unfinished("timed out after 30 seconds") }

        val result = windowsEntry(directory, runner).install(sampleLaunch())

        val refusal = assertIs<Installation.Refused>(result)
        assertTrue(refusal.message.contains("timed out after 30 seconds"), refusal.message)
        assertEquals(emptyList(), Files.list(home).use { it.toList() })
    }

    @Test
    fun `without SystemRoot no program is run and nothing is written`() = inTemporaryDirectory { home ->
        val runner = RecordingRunner()

        val result = WindowsRunEntry(ServiceDirectory(home), runner) { null }.install(sampleLaunch())

        assertIs<Installation.Refused>(result)
        assertTrue(runner.commands.isEmpty())
        assertEquals(emptyList(), Files.list(home).use { it.toList() })
    }
}
