package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.service.status.ServerState
import io.github.krosstack.kscope.service.status.ServerStatus
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerCheckTest {
    private val workspace: Path = Files.createTempDirectory("kscope-server-check").toRealPath()
    private val started: Instant = Instant.parse("2026-11-20T08:00:00Z")

    @AfterTest
    fun cleanUp() {
        workspace.toFile().deleteRecursively()
    }

    private fun write(pid: Long = PID, processStart: String? = started.toString()) {
        ServeMetadata(pid, PORT, "secret", "9.9.9", 1, "2026-11-20T08:00:03Z", processStart).write(workspace)
    }

    private fun check(facts: ProcessFacts?): ServerStatus = ServerCheck { facts }.of(workspace)

    @Test
    fun `no file means no server, and no stale file`() {
        assertEquals(ServerStatus(ServerState.NOT_RUNNING, null, null, null, stale = false), check(null))
    }

    @Test
    fun `a process that started when the server says it did is the server`() {
        write()

        assertEquals(
            ServerStatus(ServerState.RUNNING, PID, "9.9.9", started.toString(), stale = false),
            check(ProcessFacts(alive = true, startedAt = started.plusMillis(900))),
        )
    }

    @Test
    fun `a process that started more than a second away from the record is another process that reused the number`() {
        write()

        val status = check(ProcessFacts(alive = true, startedAt = started.plusMillis(1_100)))

        assertEquals(ServerState.NOT_RUNNING, status.state)
        assertEquals(true, status.stale)
    }

    @Test
    fun `a process that is gone, or never was, leaves a stale file`() {
        write()

        assertEquals(ServerStatus(ServerState.NOT_RUNNING, PID, "9.9.9", started.toString(), stale = true), check(null))
        assertEquals(ServerState.NOT_RUNNING, check(ProcessFacts(alive = false, startedAt = started)).state)
    }

    @Test
    fun `a process number that is not positive is not asked about`() {
        write(pid = 0)

        val status = ServerCheck { error("the system was asked about $it") }.of(workspace)

        assertEquals(ServerStatus(ServerState.NOT_RUNNING, 0, "9.9.9", started.toString(), stale = true), status)
    }

    @Test
    fun `a server of an older version has no recorded start, so it is not known, never running`() {
        write(processStart = null)

        val status = check(ProcessFacts(alive = true, startedAt = started))

        assertEquals(ServerState.UNKNOWN, status.state)
        assertEquals(false, status.stale)
        assertEquals("2026-11-20T08:00:03Z", status.startedAt)
    }

    @Test
    fun `a start that the system does not tell is not known, never running`() {
        write()

        assertEquals(ServerState.UNKNOWN, check(ProcessFacts(alive = true, startedAt = null)).state)
    }

    @Test
    fun `a record that is not a time is not known either`() {
        write(processStart = "yesterday")

        assertEquals(ServerState.UNKNOWN, check(ProcessFacts(alive = true, startedAt = started)).state)
    }

    private companion object {
        const val PID = 4120L
        const val PORT = 51234
    }
}
