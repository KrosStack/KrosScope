package io.github.krosstack.kscope.cli.service.home

import io.github.krosstack.kscope.cli.service.inServiceHome
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ServiceFileTest {
    private fun key(directory: Path, name: String) = WorkspacePaths.key(directory.resolve(name).toAbsolutePath())

    @Test
    fun `a file is written in its canonical form and read back`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))
        val first = key(home, "first")
        val second = key(home, "second")

        file.write(listOf(first, second))

        val expected = "# Written by kscope service add and remove.\nworkspaces = [\n  \"$first\",\n  \"$second\",\n]\n"
        assertEquals(expected, Files.readString(file.path))
        assertEquals(ServiceFileReading.Read(listOf(first, second)), file.read())
    }

    @Test
    fun `written entries are sorted and unique`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))
        val low = key(home, "a")
        val high = key(home, "b")

        file.write(listOf(high, low, high))

        assertEquals(ServiceFileReading.Read(listOf(low, high)), file.read())
        assertEquals(1, Files.readAllLines(file.path).count { it.contains(high) })
    }

    @Test
    fun `a file without entries is written and read as no workspace`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))

        file.write(emptyList())

        assertEquals(ServiceFileReading.Read(emptyList()), file.read())
    }

    @Test
    fun `a missing file means no workspace`() = inServiceHome { home ->
        assertEquals(ServiceFileReading.Read(emptyList()), ServiceFile(home.resolve("service.toml")).read())
    }

    @Test
    fun `writing replaces the file whole and leaves nothing beside it`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("nested").resolve("service.toml"))
        file.write(listOf(key(home, "a"), key(home, "b")))

        file.write(listOf(key(home, "b")))

        assertEquals(ServiceFileReading.Read(listOf(key(home, "b"))), file.read())
        val names = Files.list(file.path.parent).use { entries -> entries.map { it.fileName.toString() }.toList() }
        assertEquals(listOf("service.toml"), names)
        assertFalse(Files.readString(file.path).contains(key(home, "a")))
    }

    @Test
    fun `a path with spaces and non ASCII letters survives the round trip`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))
        val odd = key(home, "caf\u00e9 and sons")

        file.write(listOf(odd))

        assertEquals(ServiceFileReading.Read(listOf(odd)), file.read())
    }

    @Test
    fun `a path with a quote or a backslash survives the round trip where the system allows it`() =
        inServiceHome { home ->
            assumeFalse(System.getProperty("os.name").lowercase().contains("windows"))
            val file = ServiceFile(home.resolve("service.toml"))
            val odd = key(home, "a\"b") + "/c\\d"

            file.write(listOf(odd))

            assertEquals(ServiceFileReading.Read(listOf(odd)), file.read())
        }
}
