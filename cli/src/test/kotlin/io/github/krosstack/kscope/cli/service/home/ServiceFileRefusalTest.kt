package io.github.krosstack.kscope.cli.service.home

import io.github.krosstack.kscope.cli.service.inServiceHome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServiceFileRefusalTest {
    private fun unreadable(directory: Path, text: String): String {
        val file = directory.resolve("service.toml")
        Files.writeString(file, text)
        val reading = assertIs<ServiceFileReading.Unreadable>(ServiceFile(file).read())
        assertTrue(reading.message.contains("service.toml"), reading.message)
        return reading.message
    }

    @Test
    fun `an unknown key is refused with the file and the key`() = inServiceHome { home ->
        val message = unreadable(home, "# a comment\ncolour = \"red\"\n")

        assertTrue(message.contains("unknown key \"colour\""), message)
        assertTrue(message.contains("line 2"), message)
    }

    @Test
    fun `a value of the wrong type is refused with the file and the key`() = inServiceHome { home ->
        val notAnArray = unreadable(home, "workspaces = 3\n")
        val notAString = unreadable(home, "workspaces = [\n  3,\n]\n")

        assertTrue(notAnArray.contains("\"workspaces\" must be an array of strings"), notAnArray)
        assertTrue(notAnArray.contains("line 1"), notAnArray)
        assertTrue(notAString.contains("element 1 must be a string"), notAString)
        assertTrue(notAString.contains("line 2"), notAString)
    }

    @Test
    fun `broken syntax is refused with the file and the line`() = inServiceHome { home ->
        val message = unreadable(home, "workspaces = [\n  \"unfinished\n")

        assertTrue(message.contains("line 2"), message)
    }

    @Test
    fun `an entry that is not an absolute path is refused`() = inServiceHome { home ->
        val message = unreadable(home, "workspaces = [\n  \"relative/directory\",\n]\n")

        assertTrue(message.contains("element 1 is not an absolute path"), message)
    }
}
