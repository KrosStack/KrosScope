package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.inTwoScannedWorkspaces
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.stdoutLines
import io.github.krosstack.kscope.cli.service.workspaceLines
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceReloadTest {
    @Test
    fun `reload after remove closes that workspace and leaves the other server as it was`() =
        inTwoScannedWorkspaces { removed, kept ->
            inServiceHome { home ->
                withTestService(home, listOf(removed, kept)) { service ->
                    service.awaitServed(removed)
                    val survivor = service.awaitServed(kept)
                    val before = assertNotNull(ServeMetadata.read(kept))

                    val result = runService(home, "remove", "--workspace", removed.toString())

                    assertEquals(0, result.statusCode, result.output)
                    assertFalse(Files.exists(ServeMetadata.file(removed)))
                    assertEquals(before, ServeMetadata.read(kept))
                    assertEquals(0, runKscope("info", "--workspace", kept.toString()).statusCode)
                    assertEquals(1, survivor.requestCount)
                    val lines = runService(home, "status").workspaceLines()
                    assertEquals(listOf(kept.toRealPath().toString()), lines.map { it.substringBefore(": ") })
                }
            }
        }

    @Test
    fun `add while the service runs takes the new workspace up`() = inWorkspace { first ->
        inWorkspace { second ->
            inServiceHome { home ->
                withTestService(home, listOf(first)) { service ->
                    service.awaitServed(first)

                    val result = runService(home, "add", "--workspace", second.toString())

                    assertEquals(listOf("Added ${second.toRealPath()}."), result.stdoutLines())
                    service.awaitServed(second)
                    assertTrue(Files.exists(ServeMetadata.file(second)))
                    assertTrue(Files.exists(ServeMetadata.file(first)))
                }
            }
        }
    }

    @Test
    fun `a file that cannot be read keeps the current workspaces and is reported`() = inScannedWorkspace { root ->
        inServiceHome { home ->
            withTestService(home, listOf(root)) { service ->
                val server = service.awaitServed(root)
                Files.writeString(home.resolve("service.toml"), "workspaces = 3\n")

                val answer = service.control("reload")

                assertEquals(1, answer.exitCode)
                assertTrue(answer.stderr.contains("service.toml"), answer.stderr)
                assertEquals(server.metadata, ServeMetadata.read(root))
                assertEquals(0, runKscope("info", "--workspace", root.toString()).statusCode)
            }
        }
    }
}
