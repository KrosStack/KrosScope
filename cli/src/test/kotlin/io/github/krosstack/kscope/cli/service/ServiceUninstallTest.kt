package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.service.entry.CommandOutcome
import io.github.krosstack.kscope.cli.service.entry.EntryFactory
import io.github.krosstack.kscope.cli.service.entry.RecordingRunner
import io.github.krosstack.kscope.cli.service.entry.Removal
import io.github.krosstack.kscope.cli.service.entry.WindowsRunEntry
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.host.withTestService
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceUninstallTest {
    private fun uninstall(home: Path, entry: FakeSessionEntry) =
        runServiceWith(home, listOf("uninstall"), EntryFactory { entry })

    @Test
    fun `uninstall removes the entry and says so`() = inServiceHome { home ->
        val entry = FakeSessionEntry()

        val result = uninstall(home, entry)

        assertEquals(0, result.statusCode, result.output)
        assertEquals(listOf("Uninstalled."), result.stdoutLines())
        assertEquals(1, entry.uninstalls)
    }

    @Test
    fun `uninstall without an entry says so with exit code 0 and leaves a running service alone`() =
        inServiceHome { home ->
            withTestService(home, emptyList()) {
                val result = uninstall(home, FakeSessionEntry(removal = { Removal.NotInstalled }))

                assertEquals(0, result.statusCode, result.output)
                assertEquals(listOf("The service is not installed."), result.stdoutLines())
                assertTrue(Files.exists(home.resolve("service.json")))
                assertEquals(0, runService(home, "status").statusCode)
            }
        }

    @Test
    fun `uninstall stops the service when it runs`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service ->
            val result = uninstall(home, FakeSessionEntry())

            assertEquals(0, result.statusCode, result.output)
            assertEquals(listOf("Uninstalled."), result.stdoutLines())
            service.running.get(WAIT_SECONDS, TimeUnit.SECONDS)
            assertTrue(Files.notExists(home.resolve("service.json")))
        }
    }

    @Test
    fun `a refused uninstall exits with 1 and leaves a running service alone`() = inServiceHome { home ->
        withTestService(home, emptyList()) {
            val result = uninstall(home, FakeSessionEntry(removal = { Removal.Refused("could not delete the value") }))

            assertEquals(1, result.statusCode, result.output)
            assertEquals("", result.stdout)
            assertEquals("could not delete the value", result.stderr.trimEnd())
            assertEquals(0, runService(home, "status").statusCode)
        }
    }

    @Test
    fun `an error while removing is reported and exits with 1`() = inServiceHome { home ->
        val result = uninstall(home, FakeSessionEntry(removal = { throw IOException("access denied") }))

        assertEquals(1, result.statusCode, result.output)
        assertEquals("error: access denied", result.stderr.trimEnd())
    }

    @Test
    fun `a service that does not answer is reported after the entry is gone`() = inServiceHome { home ->
        SilentService().use { silent ->
            silent.announceIn(ServiceDirectory(home))

            val result = uninstall(home, FakeSessionEntry())

            assertEquals(1, result.statusCode, result.output)
            assertEquals(listOf("Uninstalled."), result.stdoutLines())
            assertEquals("The service did not answer; see `kscope service status`.", result.stderr.trimEnd())
        }
    }

    @Test
    fun `through the Windows entry uninstall deletes the value and launch args and keeps service toml`() =
        inServiceHome { home ->
            ServiceFile(home.resolve("service.toml")).write(emptyList(), ServiceLimits(idleMinutes = 7))
            val runner = RecordingRunner { CommandOutcome.Finished(0, "") }
            val entries = EntryFactory { WindowsRunEntry(it, runner, mapOf("SystemRoot" to "C:\\Windows")::get) }
            runServiceWith(home, listOf("install"), entries)
            assertTrue(Files.exists(home.resolve("launch.args")))

            val result = runServiceWith(home, listOf("uninstall"), entries)

            assertEquals(listOf("Uninstalled."), result.stdoutLines())
            assertEquals(listOf("import", "query", "delete"), runner.commands.map { it[1] })
            assertTrue(Files.notExists(home.resolve("launch.args")))
            assertTrue(Files.readString(home.resolve("service.toml")).contains("idle-minutes = 7"))
        }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
