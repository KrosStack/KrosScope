package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessState
import io.github.krosstack.kscope.service.freshness.IndexFreshness
import io.github.krosstack.kscope.service.status.IndexStatus
import io.github.krosstack.kscope.service.status.ServerState
import io.github.krosstack.kscope.service.status.ServerStatus

internal object StatusLineFixtures {
    const val TWO_DAYS = 2 * 86_400L + 30
    const val INDEX = "/work/app/.kscope"
    val noServer = ServerStatus(ServerState.NOT_RUNNING, null, null, null, stale = false)

    fun freshness(
        state: FreshnessState,
        commits: FreshnessCounts,
        trees: FreshnessCounts,
        files: Int = 0,
        estimate: Boolean = false,
    ) = IndexFreshness(state, "2026-11-20T08:00:00Z", commits, trees, files, estimate, null)

    fun line(
        freshness: IndexFreshness,
        repositories: Int,
        server: ServerStatus = noServer,
        age: Long? = TWO_DAYS,
    ): String {
        val present = IndexStatus.Present(ScanLevel.RESOLVED, freshness.scannedAt, age, freshness, repositories, 12)
        return StatusLine.of(StatusReport("/work/app", INDEX, present, server))
    }
}
