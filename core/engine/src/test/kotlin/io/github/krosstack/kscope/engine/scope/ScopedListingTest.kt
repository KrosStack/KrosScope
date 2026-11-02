package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.discovery.FileLister
import io.github.krosstack.kscope.engine.discovery.GitAccess
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopedListingTest {
    private fun listed(workspace: TestWorkspace, scope: ScanScope): List<String> =
        FileLister(workspace.root, workspace.indexDirectory, GitAccess.probe(workspace.root), scope).list().paths

    @Test
    fun `outside git the scope narrows the walk and never adds a skipped directory`() =
        TestWorkspace("plain").use { workspace ->
            workspace.write("build/Out.kt", "class Out\n")

            val paths = listed(workspace, ScanScope(include = listOf("**/*.kt", "build/**")))

            assertEquals(listOf("src/Hello.kt"), paths)
        }

    @Test
    fun `in a git work tree the scope narrows the listing and never adds an ignored file`() {
        assumeTrue(TestGit.available())
        TestWorkspace("plain").use { workspace ->
            workspace.write(".gitignore", "ignored/\n")
            workspace.write("ignored/Skipped.kt", "class Skipped\n")
            TestGit.output(workspace.root, "init", "-q")
            TestGit.output(workspace.root, "add", "-A")
            TestGit.output(workspace.root, "commit", "-q", "-m", "initial")

            val paths = listed(workspace, ScanScope(include = listOf("src", "ignored"), exclude = listOf("**/*.java")))

            assertEquals(listOf("src/Hello.kt"), paths)
        }
    }

    @Test
    fun `the scope applies to every spelling the listing yields, with the order and the notes kept apart`() =
        TestWorkspace("plain").use { workspace ->
            val lister = FileLister(
                workspace.root,
                workspace.indexDirectory,
                GitAccess.probe(workspace.root),
                ScanScope(include = listOf("nowhere"), exclude = listOf("src")),
            )

            val listing = lister.list()

            assertTrue(listing.paths.isEmpty())
            assertTrue("README.md" in listing.candidates && "src/Hello.kt" in listing.candidates)
            assertEquals(listing.candidates.sorted(), listing.candidates)
        }
}
