package io.github.krosstack.kscope.cli.service.launch

import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.service.SilentService
import io.github.krosstack.kscope.cli.service.entry.inTemporaryDirectory
import io.github.krosstack.kscope.cli.service.entry.sampleLaunch
import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundStarterTest {
    private class FakeProcess(private val exitCode: Int? = null, private val onWait: (Int) -> Unit = {}) :
        SpawnedProcess {
        var waits = 0
            private set
        var destroyed = false
            private set

        override fun awaitExit(millis: Long): Int? {
            onWait(++waits)
            return exitCode
        }

        override fun destroy() {
            destroyed = true
        }
    }

    private fun steppingClock(stepNanos: Long): () -> Long {
        var now = 0L
        return {
            now += stepNanos
            now
        }
    }

    @Test
    fun `it waits until service json names a live process whose port answers`() = inTemporaryDirectory { home ->
        val directory = ServiceDirectory(home)
        SilentService().use { service ->
            val process = FakeProcess { waits -> if (waits == 3) service.announceIn(directory) }

            val outcome = BackgroundStarter({ _, _ -> process }).start(directory, sampleLaunch())

            assertEquals(StartOutcome.Started(ProcessHandle.current().pid()), outcome)
            assertEquals(3, process.waits)
            assertFalse(process.destroyed)
        }
    }

    @Test
    fun `it stops waiting at once when the process ends without a service`() = inTemporaryDirectory { home ->
        val process = FakeProcess(exitCode = 1)

        val outcome = BackgroundStarter({ _, _ -> process }).start(ServiceDirectory(home), sampleLaunch())

        assertEquals(StartOutcome.NotStarted(), outcome)
        assertEquals(1, process.waits)
    }

    @Test
    fun `it gives up at the deadline, ends the process and ignores a service json of a dead process`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home)
            ServeMetadata(0, 0, "token", "old", 1, "then").writeTo(directory.metadataFile)
            val process = FakeProcess()
            val clock = steppingClock(SECOND)
            val starter = BackgroundStarter({ _, _ -> process }, timeoutNanos = SECOND * 30, clock = clock)

            val outcome = starter.start(directory, sampleLaunch())

            assertEquals(StartOutcome.NotStarted(), outcome)
            assertTrue(process.destroyed)
            assertTrue(process.waits in 25..35, "waited ${process.waits} times")
        }

    @Test
    fun `a process that cannot be started is reported with the reason`() = inTemporaryDirectory { home ->
        val starter = BackgroundStarter({ _, _ -> throw IOException("no such program") })

        val outcome = starter.start(ServiceDirectory(home), sampleLaunch())

        assertEquals(StartOutcome.NotStarted("no such program"), outcome)
    }

    @Test
    fun `the process is started with the launch in the service directory, which is created`() =
        inTemporaryDirectory { home ->
            val directory = ServiceDirectory(home.resolve("not yet"))
            val started = mutableListOf<Pair<ServiceLaunch, Path>>()
            val spawner = ProcessSpawner { launch, workingDirectory ->
                started += launch to workingDirectory
                FakeProcess(exitCode = 1)
            }

            BackgroundStarter(spawner).start(directory, sampleLaunch())

            assertEquals(listOf(sampleLaunch() to directory.root), started)
            assertTrue(Files.isDirectory(directory.root))
        }

    private companion object {
        const val SECOND = 1_000_000_000L
    }
}
