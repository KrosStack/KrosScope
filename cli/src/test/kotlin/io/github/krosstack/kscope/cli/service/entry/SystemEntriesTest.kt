package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SystemEntriesTest {
    private fun entryOf(osName: String, home: Path, variables: Map<String, String> = emptyMap()): SessionEntry {
        val entries = SystemEntries(osName, home, variables::get, RecordingRunner())
        return entries.entryFor(ServiceDirectory(home.resolve("service")))
    }

    @Test
    fun `the entry follows os name`() = inTemporaryDirectory { home ->
        assertIs<WindowsRunEntry>(entryOf("Windows 11", home))
        assertIs<SystemdUserEntry>(entryOf("Linux", home))
        assertIs<LaunchAgentEntry>(entryOf("Mac OS X", home))
        assertEquals(UnsupportedEntry::class, entryOf("FreeBSD", home)::class)
    }

    @Test
    fun `on any other system install and uninstall are refused with the same message and status says not registered`() =
        inTemporaryDirectory { home ->
            val entry = entryOf("Plan 9", home)
            val message = "Starting with the session is not supported on Plan 9; use `kscope service start`."

            assertEquals(Installation.Refused(message), entry.install(sampleLaunch()))
            assertEquals(Removal.Refused(message), entry.uninstall())
            assertEquals(EntryState.NotRegistered, entry.state())
            assertTrue(Files.notExists(home.resolve("service")))
        }

    @Test
    fun `the unit goes below XDG_CONFIG_HOME when it is an absolute path`() = inTemporaryDirectory { home ->
        val configured = home.resolve("xdg")

        entryOf("Linux", home, mapOf("XDG_CONFIG_HOME" to configured.toString())).install(sampleLaunch())

        assertTrue(Files.exists(unitFile(configured)))
        assertTrue(Files.notExists(unitFile(home.resolve(".config"))))
    }

    @Test
    fun `the unit goes below dot config in the home directory when XDG_CONFIG_HOME is not set, empty or relative`() =
        inTemporaryDirectory { home ->
            for (value in listOf(null, "", "  ", "relative/config")) {
                val variables = value?.let { mapOf("XDG_CONFIG_HOME" to it) }.orEmpty()

                entryOf("Linux", home, variables).install(sampleLaunch())

                assertTrue(Files.exists(unitFile(home.resolve(".config"))), "value: $value")
                Files.delete(unitFile(home.resolve(".config")))
            }
        }
}
