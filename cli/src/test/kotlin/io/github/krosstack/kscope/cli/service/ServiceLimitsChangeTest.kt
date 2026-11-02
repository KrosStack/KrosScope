package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.service.home.ServiceFile
import io.github.krosstack.kscope.cli.service.home.ServiceFileReading
import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceLimitsChangeTest {
    private val limits = ServiceLimits(idleMinutes = 7, memoryMib = 256)

    private fun reading(home: Path) = ServiceFile(home.resolve("service.toml")).read() as ServiceFileReading.Read

    @Test
    fun `add and remove keep the limits, write them only when they are set, and keep the first line`() =
        inWorkspace { first ->
            inWorkspace { second ->
                inServiceHome { home ->
                    ServiceFile(home.resolve("service.toml")).write(emptyList(), limits)

                    runService(home, "add", "--workspace", first.toString())
                    runService(home, "add", "--workspace", second.toString())
                    runService(home, "remove", "--workspace", first.toString())

                    val text = Files.readString(home.resolve("service.toml"))
                    assertEquals("# Written by kscope service add and remove.", text.lines().first())
                    assertTrue(text.endsWith("]\nidle-minutes = 7\nmemory-mib = 256\n"), text)
                    assertEquals(listOf(WorkspacePaths.key(second.toRealPath())), reading(home).workspaces)
                    assertEquals(limits, reading(home).limits)
                }
            }
        }

    @Test
    fun `add and remove write no limit that the file did not have`() = inWorkspace { root ->
        inServiceHome { home ->
            ServiceFile(home.resolve("service.toml")).write(emptyList(), ServiceLimits(memoryMib = 128))

            runService(home, "add", "--workspace", root.toString())
            runService(home, "remove", "--workspace", root.toString())

            val text = Files.readString(home.resolve("service.toml"))
            assertEquals("# Written by kscope service add and remove.\nworkspaces = []\nmemory-mib = 128\n", text)
        }
    }

    @Test
    fun `a limit that is 0 in the file stays 0 after add`() = inWorkspace { root ->
        inServiceHome { home ->
            Files.writeString(home.resolve("service.toml"), "idle-minutes = 0\n")

            runService(home, "add", "--workspace", root.toString())

            assertEquals(ServiceLimits(idleMinutes = 0), reading(home).limits)
        }
    }

    @Test
    fun `add refuses a file with a limit of the wrong type and leaves it alone`() = inWorkspace { root ->
        inServiceHome { home ->
            val file = home.resolve("service.toml")
            Files.writeString(file, "memory-mib = -4\n")

            val added = runService(home, "add", "--workspace", root.toString())

            assertEquals(1, added.statusCode, added.output)
            assertTrue(added.stderr.contains("\"memory-mib\" must be an integer of 0 or more"), added.stderr)
            assertEquals("memory-mib = -4\n", Files.readString(file))
        }
    }
}
