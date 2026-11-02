package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.model.DiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryWalkTest {
    private val plain = setOf("README.md", "LICENSE", "src/Hello.kt", "src/Hello.java", "tools/run.kts")

    @Test
    fun `a plain directory is walked and excluded directories are skipped at any depth`() =
        TestWorkspace("plain").use { workspace ->
            val excluded = listOf(".git", ".gradle", ".idea", ".kscope", "build", "out", "node_modules", "Build")
            (excluded + "src/build").forEach { workspace.write("$it/Skipped.kt", "class Skipped") }
            workspace.engine().use { engine ->
                engine.update()
                assertEquals(plain, engine.artifactPaths())
            }
        }

    @Test
    fun `a missing git executable degrades to a directory walk with an info diagnostic`() =
        TestWorkspace("plain").use { workspace ->
            val config = WorkspaceConfig(workspace.root, workspace.indexDirectory)
            ScanEngine(coreRegistry(), config, "kscope-no-such-git").use { engine ->
                val report = engine.update()
                val notice = report.diagnostics.single { it.code.value == "discovery.git-unavailable" }
                assertEquals(DiagnosticSeverity.INFO, notice.severity)
                assertEquals(plain, engine.artifactPaths())
            }
        }
}
