package io.github.krosstack.kscope.cli.service.home

import io.github.krosstack.kscope.cli.service.inServiceHome
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServiceWorkerMemoryFileTest {
    private fun key(directory: Path, name: String) = WorkspacePaths.key(directory.resolve(name).toAbsolutePath())

    private fun read(directory: Path, text: String): ServiceFileReading {
        val file = directory.resolve("service.toml")
        Files.writeString(file, text)
        return ServiceFile(file).read()
    }

    private fun refused(directory: Path, text: String): String =
        assertIs<ServiceFileReading.Unreadable>(read(directory, text)).message

    @Test
    fun `worker memory survives registration edits`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))
        val first = key(home, "first")
        val second = key(home, "second")
        file.write(listOf(first), ServiceLimits(idleMinutes = 45, workerMemoryMib = 4_096))

        val kept = assertIs<ServiceFileReading.Read>(file.read())
        file.write(kept.workspaces + second, kept.limits)
        val removed = assertIs<ServiceFileReading.Read>(file.read())
        file.write(removed.workspaces - first, removed.limits)

        val text = Files.readString(file.path)
        assertTrue(text.contains("worker-memory-mib = 4096\n"), text)
        assertEquals(
            ServiceFileReading.Read(listOf(second), ServiceLimits(idleMinutes = 45, workerMemoryMib = 4_096)),
            file.read(),
        )
        assertEquals(4_096L, file.workerMemoryMib())
    }

    @Test
    fun `graph limit defaults remain unchanged`() = inServiceHome { home ->
        val reading = assertIs<ServiceFileReading.Read>(read(home, "worker-memory-mib = 512\n"))

        assertEquals(TimeUnit.MINUTES.toMillis(30), reading.limits.idleMillis)
        assertEquals(0L, reading.limits.memoryBytes)
        assertEquals("Limits: idle 30 minutes, memory none.", reading.limits.text)
        assertEquals(ServiceLimits(workerMemoryMib = 512), reading.limits)
        assertEquals(ServiceLimits(), assertIs<ServiceFileReading.Read>(read(home, "workspaces = []\n")).limits)
    }

    @Test
    fun `a worker memory that is not a positive integer is refused with the key named`() = inServiceHome { home ->
        for (value in listOf("0", "-1", "\"big\"", "1.5")) {
            val message = refused(home, "worker-memory-mib = $value\n")

            assertTrue(message.contains("\"worker-memory-mib\" must be an integer of 1 or more"), message)
        }
    }
}
