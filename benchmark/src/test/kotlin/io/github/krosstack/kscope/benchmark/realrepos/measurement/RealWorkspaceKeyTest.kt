package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.RealWorkspaceMember
import io.github.krosstack.kscope.benchmark.realrepos.workspaceKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RealWorkspaceKeyTest {
    private val api = RealWorkspaceMember("api", "p1", "c1")
    private val app = RealWorkspaceMember("app", "p2", "c2")

    private fun task(repository: String, vararg members: RealWorkspaceMember) = RealCommitTask(
        "$repository:t", repository, "p", "c", 1, "statement", listOf("$repository/README.md"), RealSplit.DEV,
        workspace = members.toList(),
    )

    @Test
    fun `workspace tasks of different own repositories do not share a scratch workspace or an index`() {
        assertNotEquals(task("api", api, app).workspaceKey(), task("app", app, api).workspaceKey())
    }

    @Test
    fun `workspace tasks of one own repository and the same members share them whatever the member order`() {
        assertEquals(task("app", api, app).workspaceKey(), task("app", app, api).workspaceKey())
    }
}
