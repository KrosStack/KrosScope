package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.service.home.ServiceLock
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.inTwoScannedWorkspaces
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.stdoutLines
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceStopTest {
    @Test
    fun `stop closes every hosted server deletes service json and releases the lock`() =
        inTwoScannedWorkspaces { first, second ->
            inServiceHome { home ->
                withTestService(home, listOf(first, second)) { service ->
                    listOf(first, second).forEach { service.awaitServed(it) }
                    assertTrue(Files.exists(service.directory.metadataFile))

                    val answer = service.control("stop")

                    assertEquals(0, answer.exitCode)
                    assertFalse(Files.exists(ServeMetadata.file(first)))
                    assertFalse(Files.exists(ServeMetadata.file(second)))
                    assertFalse(Files.exists(service.directory.metadataFile))
                    ServiceLock.acquire(service.directory.lockFile).close()
                    service.awaitEnd()
                }
            }
        }

    @Test
    fun `the stop command says that the service stopped`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service ->
            val stop = runService(home, "stop")

            assertEquals(0, stop.statusCode, stop.output)
            assertEquals(listOf("The service stopped."), stop.stdoutLines())
            service.awaitEnd()
            assertFalse(Files.exists(service.directory.metadataFile))
        }
    }

    @Test
    fun `a service can be started again in the same directory after it stopped`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service -> service.control("stop") }

        withTestService(home, emptyList()) { service ->
            assertEquals(0, service.control("status").exitCode)
        }
    }
}
