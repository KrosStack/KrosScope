package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitDiscoveryTest {
    private fun commitAll(workspace: TestWorkspace) {
        TestGit.output(workspace.root, "init", "-q")
        TestGit.output(workspace.root, "add", "-A")
        TestGit.output(workspace.root, "commit", "-q", "-m", "initial")
    }

    @Test
    fun `a git work tree inventories tracked and untracked files but not ignored ones`() {
        assumeTrue(TestGit.available())
        TestWorkspace("plain").use { workspace ->
            workspace.write(".gitignore", "ignored.txt\ngenerated/\n")
            workspace.write("ignored.txt", "x")
            workspace.write("generated/Out.kt", "class Out")
            commitAll(workspace)
            workspace.write("Untracked.kt", "class Untracked")
            workspace.write("generated/More.kt", "class More")
            workspace.engine().use { engine ->
                engine.update()
                val plain = setOf("README.md", "LICENSE", "src/Hello.kt", "src/Hello.java", "tools/run.kts")
                assertEquals(plain + setOf(".gitignore", "Untracked.kt"), engine.artifactPaths())
            }
        }
    }

    @Test
    fun `the repository id is the root commit and the head revision is recorded`() {
        assumeTrue(TestGit.available())
        TestWorkspace("plain").use { workspace ->
            commitAll(workspace)
            workspace.engine().use { engine ->
                engine.update()
                val repository = engine.graph().structure().repositories.single()
                assertEquals(TestGit.output(workspace.root, "rev-list", "--max-parents=0", "HEAD"), repository.id.value)
                assertEquals(TestGit.output(workspace.root, "rev-parse", "HEAD"), repository.headRevision)
                assertEquals(null, repository.originUrl)
            }
        }
    }

    @Test
    fun `a repository without commits gets a persisted random id`() {
        assumeTrue(TestGit.available())
        TestWorkspace("plain").use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.engine().use { engine ->
                engine.update()
                val repository = engine.graph().structure().repositories.single()
                val persisted = Files.readString(workspace.indexDirectory.resolve("repository-id")).trim()
                assertEquals(null, repository.headRevision)
                assertEquals(persisted, repository.id.value)
                assertTrue("src/Hello.kt" in engine.artifactPaths())
            }
        }
    }

    @Test
    fun `a tracked file that was deleted from disk is not inventoried`() {
        assumeTrue(TestGit.available())
        TestWorkspace("plain").use { workspace ->
            commitAll(workspace)
            workspace.delete("src/Hello.kt")
            workspace.engine().use { engine ->
                val report = engine.update()
                assertTrue("src/Hello.kt" !in engine.artifactPaths())
                assertTrue(report.diagnostics.none { it.code.value == "discovery.unreadable-file" })
            }
        }
    }
}
