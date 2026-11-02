package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.stdoutLines
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceWaitingTest {
    private fun printed(root: Path) = WorkspacePaths.real(root).toString()

    @Test
    fun `a workspace that another server holds waits and is served after that server stops`() =
        inScannedWorkspace { root ->
            inServiceHome { home ->
                withTestServer(root, ServeSettings(watch = quietWatch())) { holder ->
                    withTestService(home, listOf(root)) { service ->
                        service.awaitState(root) { it == WorkspaceState.Waiting }
                        val reason = "waiting, because a server of this workspace runs in another process."
                        assertEquals("${printed(root)}: $reason", runService(home, "status").stdoutLines().last())

                        holder.requestStop()
                        holder.awaitTermination()
                        service.retryAfter(RETRY_SECONDS)

                        service.awaitServed(root)
                        val served = runService(home, "status").stdoutLines().last()
                        assertTrue(served.startsWith("${printed(root)}: served. "), served)
                    }
                }
            }
        }

    @Test
    fun `a directory that does not exist is not served and is tried again`() = inWorkspace { parent ->
        inServiceHome { home ->
            val later = parent.resolve("later")
            withTestService(home, listOf(later)) { service ->
                service.awaitState(later) { it is WorkspaceState.NotServed }

                val status = runService(home, "status")

                val expected = "${printed(later)}: not served, because the directory does not exist."
                assertEquals(expected, status.stdoutLines().last())
                Files.createDirectory(later)
                service.retryAfter(RETRY_SECONDS)
                assertTrue(Files.exists(ServeMetadata.file(service.awaitServed(later).workspace)))
            }
        }
    }

    private companion object {
        const val RETRY_SECONDS = 31L
    }
}
