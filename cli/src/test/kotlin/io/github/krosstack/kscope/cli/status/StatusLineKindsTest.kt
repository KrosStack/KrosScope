package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.cli.status.StatusLineFixtures.freshness
import io.github.krosstack.kscope.cli.status.StatusLineFixtures.line
import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessState
import io.github.krosstack.kscope.service.status.IndexStatus
import io.github.krosstack.kscope.service.status.ServerState
import io.github.krosstack.kscope.service.status.ServerStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusLineKindsTest {
    private val index = StatusLineFixtures.INDEX

    @Test
    fun `the server is running, not running or not known, and unknown is never running`() {
        val same = freshness(FreshnessState.NOT_BEHIND, FreshnessCounts(0, 1, 0), FreshnessCounts(0, 1, 0))
        val running = ServerStatus(ServerState.RUNNING, 4120, "1.0", "then", stale = false)
        val unknown = ServerStatus(ServerState.UNKNOWN, 4120, "1.0", "then", stale = false)
        val stale = ServerStatus(ServerState.NOT_RUNNING, 4120, "1.0", "then", stale = true)

        assertTrue(line(same, 1, running).endsWith("; server running (pid 4120)"))
        assertTrue(line(same, 1, unknown).endsWith("; server unknown (pid 4120 is alive, but it cannot be checked)"))
        assertFalse(line(same, 1, unknown).contains("running"))
        assertTrue(line(same, 1, stale).endsWith("; server not running"))
    }

    @Test
    fun `there is a line for no index, another format and an index that cannot be read`() {
        val none = StatusLineFixtures.noServer

        fun of(status: IndexStatus) = StatusLine.of(StatusReport("/work/app", index, status, none))

        assertEquals("kscope: no index in /work/app; run kscope scan", of(IndexStatus.NoIndex))
        assertEquals(
            "kscope: the index in $index is of another format (version 5; this kscope reads version 6); " +
                "run kscope scan to rebuild it",
            of(IndexStatus.OtherFormat(5, 6)),
        )
        assertEquals(
            "kscope: the index in $index cannot be read (file is not a database); run kscope scan --rebuild",
            of(IndexStatus.Damaged("file is not a database")),
        )
    }
}
