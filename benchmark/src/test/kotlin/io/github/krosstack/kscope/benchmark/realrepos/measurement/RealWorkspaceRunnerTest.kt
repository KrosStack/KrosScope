package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.RealWorkspaceMember
import io.github.krosstack.kscope.benchmark.realrepos.report.RealRepositoryReports
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealWorkspaceRunnerTest {
    @TempDir
    lateinit var output: Path

    @Test
    fun `a task over two repositories is indexed from side by side checkouts with workspace gold paths`() {
        CorpusRepository("api").use { api ->
            CorpusRepository("app").use { app ->
                val apiParent = api.commit("Initial", mapOf("README.md" to "interface anchors before\n"))
                val apiChild = api.commit("Change", mapOf("README.md" to "interface anchors after\n"))
                val appParent = app.commit("Initial", mapOf("README.md" to "consumer anchors before\n"))
                val appChild = app.commit("Change", mapOf("README.md" to "consumer anchors after\n"))
                val task = RealCommitTask(
                    "app:both", "app", appParent, appChild, 1, "interface consumer anchors",
                    listOf("api/README.md", "app/README.md"), RealSplit.TEST, false, 4, false,
                    listOf(
                        RealWorkspaceMember("api", apiParent, apiChild),
                        RealWorkspaceMember("app", appParent, appChild),
                    ),
                )
                val manifest = RealRepositoryManifest(listOf(api.spec(), app.spec()), listOf(task), emptyMap(), "test")

                val result = RealRepositoryRunner().run(manifest, RealRunOptions(output, IDENTITY)).single()

                assertEquals(RealMeasurementStatus.COMPLETE, result.status, result.failure)
                val context = assertNotNull(result.context).budgets.first().tasks.single()
                assertEquals(setOf("api/README.md", "app/README.md"), context.resolvedGoldFiles.toSet())
                val scratch = Files.list(output.resolve("scratch")).use { it.toList() }.single()
                assertTrue(Files.isDirectory(scratch.resolve("api/.git")))
                assertTrue(Files.isDirectory(scratch.resolve("app/.git")))
                assertEquals("interface anchors after\n", Files.readString(scratch.resolve("api/README.md")))
                assertEquals("interface anchors after\n", Files.readString(api.root.resolve("README.md")))
                val levelFour = RealRepositoryReports.aggregate(manifest, listOf(result)).classGroups
                    .single { it.taskClass?.label == "Level 4" }
                assertEquals(1, levelFour.measured)
                assertEquals("Level 4", levelFour.taskClass?.label)
            }
        }
    }

    @Test
    fun `workspace members must be known directories holding the task repository and its gold`() {
        CorpusRepository("api").use { api ->
            CorpusRepository("app").use { app ->
                val sha = api.commit("Initial", mapOf("README.md" to "text\n"))
                val appSha = app.commit("Initial", mapOf("README.md" to "text\n"))
                val member = RealWorkspaceMember("app", appSha, appSha)
                val task = RealCommitTask(
                    "app:both", "app", appSha, appSha, 1, "text", listOf("app/README.md"), RealSplit.TEST,
                    workspace = listOf(RealWorkspaceMember("api", sha, sha), member),
                )
                val manifest = RealRepositoryManifest(listOf(api.spec(), app.spec()), listOf(task), emptyMap(), "test")
                val runner = RealRepositoryRunner()
                listOf(
                    task.copy(workspace = listOf(RealWorkspaceMember("api", sha, sha))),
                    task.copy(workspace = listOf(member, member)),
                    task.copy(workspace = listOf(RealWorkspaceMember("other", sha, sha), member)),
                    task.copy(goldFiles = listOf("README.md")),
                    task.copy(level = 7),
                ).forEach { invalid ->
                    assertFailsWith<IllegalArgumentException> {
                        runner.run(manifest.copy(tasks = listOf(invalid)), RealRunOptions(output, IDENTITY))
                    }
                }
            }
        }
    }

    private companion object {
        val IDENTITY = "a".repeat(64)
    }
}
