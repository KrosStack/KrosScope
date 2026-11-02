package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.service.entry.SessionLine
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceNotRunningTest {
    private val notRunning = "The service does not run."
    private val stale = ServeMetadata(0, 0, "unused", "stale", 1, "then")

    @Test
    fun `the stop command says so when no service runs`() = inServiceHome { home ->
        val stop = runService(home, "stop")

        assertEquals(0, stop.statusCode, stop.output)
        assertEquals(listOf("The service does not run."), stop.stdoutLines())
    }

    @Test
    fun `the status command says so when no service runs`() = inServiceHome { home ->
        val status = runService(home, "status")

        assertEquals(1, status.statusCode, status.output)
        assertEquals(listOf("The service does not run.", SessionLine.NOT_REGISTERED), status.stdoutLines())
    }

    @Test
    fun `a stale service json is ignored by status stop and add`() = inWorkspace { root ->
        inServiceHome { home ->
            stale.writeTo(home.resolve("service.json"))

            val status = runService(home, "status")
            val stop = runService(home, "stop")
            val add = runService(home, "add", "--workspace", root.toString())

            assertEquals(1, status.statusCode, status.output)
            assertEquals(listOf(notRunning, SessionLine.NOT_REGISTERED), status.stdoutLines())
            assertEquals(0, stop.statusCode, stop.output)
            assertEquals(listOf(notRunning), stop.stdoutLines())
            assertEquals(0, add.statusCode, add.output)
            assertEquals(
                listOf("Added ${root.toRealPath()}.", "The service does not run; start it with `kscope service run`."),
                add.stdoutLines(),
            )
        }
    }
}
