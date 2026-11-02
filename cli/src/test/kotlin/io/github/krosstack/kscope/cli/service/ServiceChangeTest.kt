package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceFileReading
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceChangeTest {
    private val notRunning = "The service does not run; start it with `kscope service run`."

    private fun kept(home: Path): List<String> =
        (ServiceFile(home.resolve("service.toml")).read() as ServiceFileReading.Read).workspaces

    @Test
    fun `add keeps a workspace once and says so when it is kept already`() = inWorkspace { root ->
        inServiceHome { home ->
            val path = root.toRealPath()

            val first = runService(home, "add", "--workspace", root.toString())
            val second = runService(home, "add", "--workspace", root.toString())

            assertEquals(0, first.statusCode, first.output)
            assertEquals(listOf("Added $path.", notRunning), first.stdoutLines())
            assertEquals(0, second.statusCode, second.output)
            assertEquals(listOf("$path is kept already.", notRunning), second.stdoutLines())
            assertEquals(listOf(WorkspacePaths.key(path)), kept(home))
        }
    }

    @Test
    fun `add writes the canonical file under the directory named by the variable`() = inWorkspace { root ->
        inServiceHome { home ->
            runService(home, "add", "--workspace", root.toString())

            val key = WorkspacePaths.key(root.toRealPath())
            val expected = "# Written by kscope service add and remove.\nworkspaces = [\n  \"$key\",\n]\n"
            assertEquals(expected, Files.readString(home.resolve("service.toml")))
        }
    }

    @Test
    fun `add refuses a directory that does not exist`() = inServiceHome { home ->
        val result = runService(home, "add", "--workspace", home.resolve("missing").toString())

        assertEquals(2, result.statusCode, result.output)
        assertFalse(Files.exists(home.resolve("service.toml")))
    }

    @Test
    fun `remove drops a kept workspace and says so when it was not kept`() = inWorkspace { root ->
        inServiceHome { home ->
            val path = root.toRealPath()
            runService(home, "add", "--workspace", root.toString())

            val removed = runService(home, "remove", "--workspace", root.toString())
            val again = runService(home, "remove", "--workspace", root.toString())

            assertEquals(0, removed.statusCode, removed.output)
            assertEquals(listOf("Removed $path."), removed.stdoutLines())
            assertEquals(0, again.statusCode, again.output)
            assertEquals(listOf("$path is not kept."), again.stdoutLines())
            assertEquals(emptyList(), kept(home))
        }
    }

    @Test
    fun `remove works for a directory that no longer exists`() = inWorkspace { parent ->
        inServiceHome { home ->
            val gone = Files.createDirectory(parent.resolve("gone"))
            val path = gone.toRealPath()
            runService(home, "add", "--workspace", gone.toString())
            Files.delete(gone)

            val result = runService(home, "remove", "--workspace", gone.toString())

            assertEquals(0, result.statusCode, result.output)
            assertEquals(listOf("Removed $path."), result.stdoutLines())
            assertEquals(emptyList(), kept(home))
        }
    }

    @Test
    fun `add and remove refuse a file that cannot be read and leave it alone`() = inWorkspace { root ->
        inServiceHome { home ->
            val file = home.resolve("service.toml")
            Files.writeString(file, "workspaces = 3\n")

            val added = runService(home, "add", "--workspace", root.toString())
            val removed = runService(home, "remove", "--workspace", root.toString())

            for (result in listOf(added, removed)) {
                assertEquals(1, result.statusCode, result.output)
                assertTrue(result.stderr.contains("service.toml"), result.stderr)
                assertEquals("", result.stdout)
            }
            assertEquals("workspaces = 3\n", Files.readString(file))
        }
    }
}
