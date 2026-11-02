package io.github.krosstack.kscope.cli.service.home

import io.github.krosstack.kscope.cli.service.inServiceHome
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServiceLimitsFileTest {
    private fun key(directory: Path, name: String) = WorkspacePaths.key(directory.resolve(name).toAbsolutePath())

    private fun read(directory: Path, text: String): ServiceFileReading {
        val file = directory.resolve("service.toml")
        Files.writeString(file, text)
        return ServiceFile(file).read()
    }

    private fun refused(directory: Path, text: String): String {
        val message = assertIs<ServiceFileReading.Unreadable>(read(directory, text)).message
        assertTrue(message.contains("service.toml"), message)
        return message
    }

    @Test
    fun `the two limits are written after the workspaces and read back`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))
        val workspace = key(home, "first")

        file.write(listOf(workspace), ServiceLimits(idleMinutes = 45, memoryMib = 2_048))

        val expected = "# Written by kscope service add and remove.\nworkspaces = [\n  \"$workspace\",\n]\n" +
            "idle-minutes = 45\nmemory-mib = 2048\n"
        assertEquals(expected, Files.readString(file.path))
        assertEquals(
            ServiceFileReading.Read(listOf(workspace), ServiceLimits(idleMinutes = 45, memoryMib = 2_048)),
            file.read(),
        )
    }

    @Test
    fun `a limit that is not set is not written`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))

        file.write(emptyList(), ServiceLimits(memoryMib = 512))
        assertEquals(
            "# Written by kscope service add and remove.\nworkspaces = []\nmemory-mib = 512\n",
            Files.readString(file.path),
        )

        file.write(emptyList(), ServiceLimits(idleMinutes = 0))
        assertEquals(
            "# Written by kscope service add and remove.\nworkspaces = []\nidle-minutes = 0\n",
            Files.readString(file.path),
        )
    }

    @Test
    fun `absent keys give 30 minutes and no ceiling`() = inServiceHome { home ->
        val reading = assertIs<ServiceFileReading.Read>(read(home, "workspaces = []\n"))

        assertEquals(TimeUnit.MINUTES.toMillis(30), reading.limits.idleMillis)
        assertEquals(0L, reading.limits.memoryBytes)
        assertEquals(ServiceLimits(), reading.limits)
    }

    @Test
    fun `a limit of 0 means never for the idle time and no ceiling for the memory`() = inServiceHome { home ->
        val reading = assertIs<ServiceFileReading.Read>(read(home, "idle-minutes = 0\nmemory-mib = 0\n"))

        assertEquals(0L, reading.limits.idleMillis)
        assertEquals(0L, reading.limits.memoryBytes)
    }

    @Test
    fun `the limits convert to milliseconds and bytes without overflowing`() = inServiceHome { home ->
        val reading = assertIs<ServiceFileReading.Read>(read(home, "idle-minutes = 2\nmemory-mib = 3\n"))
        val huge = assertIs<ServiceFileReading.Read>(
            read(home, "idle-minutes = 9223372036854775807\nmemory-mib = 9223372036854775807\n"),
        )

        assertEquals(120_000L, reading.limits.idleMillis)
        assertEquals(3L * 1_048_576, reading.limits.memoryBytes)
        assertEquals(Long.MAX_VALUE, huge.limits.idleMillis)
        assertTrue(huge.limits.memoryBytes > 0)
    }

    @Test
    fun `a negative number a string and a float are refused with the key named`() = inServiceHome { home ->
        for (key in listOf("idle-minutes", "memory-mib")) {
            for (value in listOf("-1", "\"ten\"", "1.5", "true", "[1]")) {
                val message = refused(home, "# comment\n$key = $value\n")

                assertTrue(message.contains("\"$key\" must be an integer of 0 or more"), message)
                assertTrue(message.contains("line 2"), message)
            }
        }
    }

    @Test
    fun `the key that is wrong is the one that is named`() = inServiceHome { home ->
        val message = refused(home, "idle-minutes = 5\nmemory-mib = -2\n")

        assertTrue(message.contains("\"memory-mib\""), message)
        assertTrue(message.contains("line 2"), message)
    }

    @Test
    fun `an unknown key is refused and the message lists the three keys`() = inServiceHome { home ->
        val message = refused(home, "idle-minute = 5\n")

        assertTrue(message.contains("unknown key \"idle-minute\""), message)
        assertTrue(message.contains("\"workspaces\", \"idle-minutes\" and \"memory-mib\""), message)
    }
}
