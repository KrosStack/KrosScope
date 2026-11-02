package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LaunchAgentEntryTest {
    private fun plist(home: Path): Path =
        home.resolve("Library").resolve("LaunchAgents").resolve("io.github.krosstack.kscope.plist")

    @Test
    fun `install writes the launch agent that runs at load and does not keep alive`() =
        inTemporaryDirectory("with space") { home ->
            val directory = ServiceDirectory(home.resolve("service dir"))
            val launch = sampleLaunch("jdk & co/bin/java")

            val result = LaunchAgentEntry(directory, home).install(launch)

            assertEquals(Installation.Installed, result)
            val text = Files.readString(plist(home))
            assertTrue(text.contains("<key>Label</key>\n  <string>io.github.krosstack.kscope</string>"), text)
            assertTrue(text.contains("<key>RunAtLoad</key>\n  <true/>"), text)
            assertFalse(text.contains("KeepAlive"), text)
            assertTrue(text.contains("jdk &amp; co"), text)
            assertEquals(launch.arguments, directory.writtenArguments())
        }

    @Test
    fun `the program arguments are the java executable and the argument file`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home.resolve("service"))
        val entry = LaunchAgentEntry(directory, home)

        entry.install(sampleLaunch("jdk & co/bin/java"))

        val command = assertNotNull(assertIs<EntryState.Registered>(entry.state()).command)
        assertEquals(sampleLaunch("jdk & co/bin/java").executable.toString(), command.java)
        assertEquals(directory.launchFile.toString(), command.argumentFile)
    }

    @Test
    fun `a second install replaces the file`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home.resolve("service"))
        val entry = LaunchAgentEntry(directory, home)
        entry.install(sampleLaunch("first/java"))

        val result = entry.install(sampleLaunch("second/java"))

        assertEquals(Installation.Installed, result)
        val text = Files.readString(plist(home))
        assertTrue(text.contains("second"), text)
        assertFalse(text.contains("first"), text)
        assertEquals(listOf("io.github.krosstack.kscope.plist"), namesIn(plist(home).parent))
        assertEquals(listOf("launch.args"), namesIn(directory.root))
    }

    @Test
    fun `uninstall deletes the launch agent and the argument file`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home.resolve("service"))
        val entry = LaunchAgentEntry(directory, home)
        entry.install(sampleLaunch())

        val result = entry.uninstall()

        assertEquals(Removal.Removed, result)
        assertTrue(Files.notExists(plist(home)))
        assertTrue(Files.notExists(directory.launchFile))
        assertEquals(EntryState.NotRegistered, entry.state())
    }

    @Test
    fun `uninstall when nothing is installed says so`() = inTemporaryDirectory { home ->
        val result = LaunchAgentEntry(ServiceDirectory(home.resolve("service")), home).uninstall()

        assertEquals(Removal.NotInstalled, result)
    }
}
