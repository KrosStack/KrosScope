package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.TemporaryTree
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHeadCommitTest {
    private val base: Path = Files.createTempDirectory("kscope-head-commit").toRealPath()
    private val checkout: Path = base.resolve("checkout")

    @BeforeTest
    fun createCheckout() {
        assumeTrue(TestGit.available())
        Files.createDirectories(checkout.resolve("lib"))
        Files.writeString(checkout.resolve("lib/A.kt"), "class A\n")
        TestGit.output(checkout, "init", "-q")
        commit(checkout, "first")
    }

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    private fun commit(directory: Path, message: String) {
        TestGit.output(directory, "add", "-A")
        TestGit.output(directory, "commit", "-q", "-m", message)
    }

    private fun head(directory: Path): String = TestGit.output(directory, "rev-parse", "HEAD")

    private fun branch(): String = TestGit.output(checkout, "symbolic-ref", "HEAD")

    @Test
    fun `a branch with a loose reference file gives its commit also from a folder below`() {
        assertTrue(Files.isRegularFile(checkout.resolve(".git").resolve(branch())))

        assertEquals(head(checkout), GitHeadCommit.of(checkout))
        assertEquals(head(checkout), GitHeadCommit.of(checkout.resolve("lib")))
    }

    @Test
    fun `a branch that is only in the packed references gives its commit`() {
        val loose = checkout.resolve(".git").resolve(branch())
        TestGit.output(checkout, "pack-refs", "--all")

        assertFalse(Files.exists(loose))
        assertEquals(head(checkout), GitHeadCommit.of(checkout))
    }

    @Test
    fun `a detached head gives its commit`() {
        Files.writeString(checkout.resolve("lib/A.kt"), "class A2\n")
        commit(checkout, "second")
        TestGit.output(checkout, "checkout", "-q", "--detach", "HEAD~1")

        assertEquals(head(checkout), GitHeadCommit.of(checkout))
    }

    @Test
    fun `a work tree with a gitdir file gives the commit of that work tree`() {
        val tree = base.resolve("tree")
        TestGit.output(checkout, "worktree", "add", "-q", "-b", "other", tree.toString())
        Files.writeString(tree.resolve("lib/A.kt"), "class Other\n")
        commit(tree, "other")

        assertTrue(Files.isRegularFile(tree.resolve(".git")))
        assertEquals(head(tree), GitHeadCommit.of(tree))
        assertNotEquals(head(checkout), GitHeadCommit.of(tree))
        assertEquals(head(checkout), GitHeadCommit.of(checkout))
    }

    @Test
    fun `a reference that the files cannot resolve gives no commit so that Git is asked`() {
        val commit = head(checkout)
        TestGit.output(checkout, "symbolic-ref", "refs/heads/alias", branch())
        TestGit.output(checkout, "symbolic-ref", "HEAD", "refs/heads/alias")
        val lost = Files.createDirectories(base.resolve("lost"))
        Files.writeString(lost.resolve(".git"), "gitdir: ../nowhere\n")

        assertEquals(commit, head(checkout))
        assertNull(GitHeadCommit.of(checkout))
        assertNull(GitHeadCommit.of(lost))
    }
}
