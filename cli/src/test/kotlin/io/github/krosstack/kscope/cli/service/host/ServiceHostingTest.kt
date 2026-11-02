package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.inTwoScannedWorkspaces
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.stdoutLines
import io.github.krosstack.kscope.cli.service.workspaceLines
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceHostingTest {
    private fun printed(root: Path) = WorkspacePaths.real(root).toString()

    @Test
    fun `two workspaces are hosted by one process and each answers like a server of its own`() =
        inTwoScannedWorkspaces { first, second ->
            inServiceHome { home ->
                withTestService(home, listOf(first, second)) { service ->
                    val servers = listOf(first, second).map { service.awaitServed(it) }
                    for (root in listOf(first, second)) {
                        assertNotNull(ServeMetadata.read(root))
                        val local = runKscope("info", "--workspace", root.toString(), "--no-server")
                        val hosted = runKscope("info", "--workspace", root.toString())
                        assertEquals(local.stdout, hosted.stdout)
                        assertEquals(local.statusCode, hosted.statusCode)
                    }
                    assertEquals(listOf(1, 1), servers.map { it.requestCount })
                    assertEquals(
                        servers.map { it.metadata.port }.toSet().size,
                        servers.size,
                        "each server has a port of its own",
                    )
                }
            }
        }

    @Test
    fun `status says what the service does with each workspace in the order of the file`() =
        inTwoScannedWorkspaces { first, second ->
            inServiceHome { home ->
                withTestService(home, listOf(first, second)) { service ->
                    val servers = listOf(first, second).map { service.awaitServed(it) }

                    val status = runService(home, "status")

                    val lines = status.stdoutLines()
                    val pid = ProcessHandle.current().pid()
                    val version = KrosScope.toolVersion()
                    assertEquals(0, status.statusCode, status.output)
                    assertTrue(lines.first().startsWith("The service runs: pid $pid, version $version, started "))
                    assertTrue(lines.first().endsWith("."), lines.first())
                    val inFileOrder = servers.sortedBy { WorkspacePaths.key(it.workspace) }
                    assertEquals(
                        inFileOrder.map {
                            "${it.workspace}: served. ${it.watchState?.status()} Index level: resolved."
                        },
                        status.workspaceLines(),
                    )
                }
            }
        }

    @Test
    fun `status of a service that keeps no workspace says how to add one`() = inServiceHome { home ->
        withTestService(home, emptyList()) {
            val status = runService(home, "status")

            assertEquals(0, status.statusCode, status.output)
            assertEquals("It keeps no workspace. Add one with `kscope service add`.", status.stdoutLines().last())
        }
    }

    @Test
    fun `the service answers while a workspace is still loading`() = inWorkspace { root ->
        inServiceHome { home ->
            val loading = CountDownLatch(1)
            val release = CountDownLatch(1)
            val watch = quietWatch().copy(requiresTrust = {
                loading.countDown()
                check(release.await(60, TimeUnit.SECONDS))
                false
            })
            withTestService(home, listOf(root), ServiceSettings(ServeSettings(watch = watch))) { service ->
                assertTrue(loading.await(60, TimeUnit.SECONDS))

                val status = runService(home, "status")

                assertEquals(0, status.statusCode, status.output)
                assertEquals("${printed(root)}: starting.", status.stdoutLines().last())
                release.countDown()
                service.awaitServed(root)
                assertTrue(Files.exists(ServeMetadata.file(root)))
            }
        }
    }
}
