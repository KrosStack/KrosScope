package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.stdoutLines
import io.github.krosstack.kscope.cli.service.workspaceLines
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceErrorTest {
    private val memory = "it does not fit in memory"

    private fun notServed(reason: String): (WorkspaceState) -> Boolean = { it == WorkspaceState.NotServed(reason) }

    @Test
    fun `an Error while a workspace opens ends that workspace and not the thread that opens the others`() =
        inWorkspace { first ->
            inWorkspace { second ->
                inServiceHome { home ->
                    val calls = AtomicInteger()
                    val opening = { path: Path, serve: ServeSettings, scan: ScanRunner ->
                        when (calls.incrementAndGet()) {
                            1 -> throw OutOfMemoryError()
                            2 -> throw StackOverflowError()
                            else -> ServeServer.open(path, serve, scan)
                        }
                    }
                    val settings = ServiceSettings(ServeSettings(watch = quietWatch()), opening = opening)
                    withTestService(home, listOf(first, second), settings) { service ->
                        val reasons = listOf(first, second).map { root ->
                            val failed = service.awaitState(root) { it is WorkspaceState.NotServed }
                            (failed as WorkspaceState.NotServed).reason
                        }
                        assertEquals(setOf(memory, "StackOverflowError"), reasons.toSet())
                        val lines = runService(home, "status").workspaceLines()
                        assertTrue(lines.all { it.contains(": not served, because ") }, lines.toString())

                        service.retryAfter(RETRY_SECONDS)

                        listOf(first, second).forEach { service.awaitServed(it) }
                        assertEquals(0, service.control("status").exitCode)
                    }
                }
            }
        }

    @Test
    fun `an Error in a running server ends that server and the workspace is opened again`() = inWorkspace { root ->
        inServiceHome { home ->
            val calls = AtomicInteger()
            val serving = { server: ServeServer ->
                when (calls.incrementAndGet()) {
                    1 -> failAfterClosing(server, OutOfMemoryError())
                    2 -> failAfterClosing(server, StackOverflowError("too deep"))
                    else -> server.run()
                }
            }
            val settings = ServiceSettings(ServeSettings(watch = quietWatch()), serving = serving)
            withTestService(home, listOf(root), settings) { service ->
                service.awaitState(root, notServed(memory))
                assertFalse(Files.exists(ServeMetadata.file(root)))
                assertEquals(
                    "${WorkspacePaths.real(root)}: not served, because $memory.",
                    runService(home, "status").stdoutLines().last(),
                )

                service.retryAfter(RETRY_SECONDS)
                service.awaitState(root, notServed("StackOverflowError: too deep"))
                service.retryAfter(RETRY_SECONDS)

                assertTrue(Files.exists(ServeMetadata.file(service.awaitServed(root).workspace)))
                assertEquals(0, service.control("status").exitCode)
            }
        }
    }

    private fun failAfterClosing(server: ServeServer, error: Error) {
        try {
            throw error
        } finally {
            server.close()
        }
    }

    private companion object {
        const val RETRY_SECONDS = 31L
    }
}
