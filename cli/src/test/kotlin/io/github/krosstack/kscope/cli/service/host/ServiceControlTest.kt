package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeWire
import io.github.krosstack.kscope.cli.serve.token.assertReadableByOwnerOnly
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.runService
import io.github.krosstack.kscope.cli.service.stdoutLines
import java.net.Socket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceControlTest {
    @Test
    fun `a second service run with the same directory is refused`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service ->
            val second = runService(home, "run")

            assertEquals(1, second.statusCode, second.output)
            assertEquals("The service runs already.", second.stderr.trimEnd())
            assertEquals("", second.stdout)
            assertEquals(0, service.control("status").exitCode)
        }
    }

    @Test
    fun `a control request without the right token gets no answer and changes nothing`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service ->
            for (action in listOf("stop", "reload", "status")) {
                val request = ServeRequest(token = "wrong", action = action)
                Socket("127.0.0.1", service.instance.metadata.port).use { socket ->
                    socket.soTimeout = READ_TIMEOUT_MILLIS
                    socket.getOutputStream().write((ServeWire.encode(request) + "\n").toByteArray(Charsets.UTF_8))
                    assertEquals(-1, socket.getInputStream().read(), action)
                }
            }

            assertEquals(0, service.control("status").exitCode)
            assertTrue(Files.exists(service.directory.metadataFile))
        }
    }

    @Test
    fun `a control request for anything else than the three actions is refused`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service ->
            val answer = service.control("command")

            assertEquals(2, answer.exitCode)
            assertEquals("This command is not served.", answer.stderr.trimEnd())
        }
    }

    @Test
    fun `a service of another version is still stopped and still answers status`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service ->
            val other = service.instance.metadata.copy(toolVersion = "0.0.0-other")
            other.writeTo(service.directory.metadataFile)

            val status = runService(home, "status")
            val stop = runService(home, "stop")

            assertEquals(0, status.statusCode, status.output)
            assertTrue(status.stdoutLines().first().contains("version 0.0.0-other"), status.output)
            assertEquals(listOf("The service stopped."), stop.stdoutLines())
            service.awaitEnd()
        }
    }

    @Test
    fun `the token file of the service can be read by its owner only`() = inServiceHome { home ->
        withTestService(home, emptyList()) { service ->
            assertReadableByOwnerOnly(service.directory.metadataFile)
        }
    }

    private companion object {
        const val READ_TIMEOUT_MILLIS = 10_000
    }
}
