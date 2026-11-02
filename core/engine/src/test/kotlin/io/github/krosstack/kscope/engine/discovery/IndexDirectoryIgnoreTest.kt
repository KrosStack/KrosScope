package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexDirectoryIgnoreTest {
    private fun inTree(block: (TestWorkspace) -> Unit) = TestWorkspace("plain").use(block)

    private fun engineInTree(workspace: TestWorkspace) = ScanEngine(coreRegistry(), WorkspaceConfig(workspace.root))

    @Test
    fun `a new index directory ignores everything in it, itself included`() = inTree { workspace ->
        engineInTree(workspace).use { it.update() }

        val ignore = workspace.root.resolve(".kscope/.gitignore")
        assertTrue(Files.isRegularFile(ignore))
        assertTrue(Files.readAllLines(ignore).any { it == "*" })
    }

    @Test
    fun `a scan of a git work tree leaves git status as it was`() = inTree { workspace ->
        assumeTrue(TestGit.available())
        TestGit.output(workspace.root, "init", "-q")
        TestGit.output(workspace.root, "add", "-A")
        TestGit.output(workspace.root, "commit", "-q", "-m", "initial")
        assertEquals("", TestGit.output(workspace.root, "status", "--porcelain"))

        engineInTree(workspace).use { it.update() }

        assertTrue(Files.isRegularFile(workspace.root.resolve(".kscope/index.db")))
        assertEquals(
            "",
            TestGit.output(workspace.root, "status", "--porcelain", "--ignored=no", "--untracked-files=all"),
        )
    }

    @Test
    fun `the ignore file is not part of the scanned workspace and verification still matches`() = inTree { workspace ->
        engineInTree(workspace).use { engine ->
            engine.update()
            val first = engine.artifactPaths()
            val second = engine.update(verify = true)

            assertTrue(first.none { it.startsWith(".kscope") }, "the index directory was scanned: $first")
            assertEquals(first, engine.artifactPaths())
            assertEquals(0, second.unitsProduced)
            assertTrue(second.verify?.matches == true)
        }
    }

    @Test
    fun `an index directory inside the tree finds the same workspace as one beside it`() = inTree { workspace ->
        val beside = workspace.engine().use { engine ->
            engine.update()
            engine.artifactPaths()
        }
        val inside = engineInTree(workspace).use { engine ->
            engine.update()
            engine.artifactPaths()
        }

        assertEquals(beside, inside)
    }

    @Test
    fun `a directory that already exists is left as it is`() = inTree { workspace ->
        val existing = workspace.root.resolve("existing-index")
        Files.createDirectories(existing)
        val config = WorkspaceConfig(workspace.root, existing)

        ScanEngine(coreRegistry(), config).use { it.update() }

        assertTrue(Files.notExists(existing.resolve(".gitignore")))
        assertTrue(Files.isRegularFile(existing.resolve("index.db")))
    }
}
