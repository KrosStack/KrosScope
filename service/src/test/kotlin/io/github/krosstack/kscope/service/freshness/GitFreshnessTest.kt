package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GitFreshnessTest {
    private val base: Path = Files.createTempDirectory("kscope-git-freshness").toRealPath()
    private val root: Path = base.resolve("checkout")
    private val none = FreshnessCounts(0, 0, 0)
    private val same = FreshnessCounts(0, 1, 0)
    private val other = FreshnessCounts(1, 0, 0)
    private val unchecked = FreshnessCounts(0, 0, 1)

    @BeforeTest
    fun createCheckout() {
        assumeTrue(TestGit.available())
        FreshnessFixtures.repository(root)
    }

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    @Test
    fun `a checkout that was scanned at its commit with nothing changed is not behind`() {
        FreshnessFixtures.scanned(root)

        val freshness = FreshnessFixtures.freshness(root)

        val scannedAt = assertNotNull(freshness.scannedAt)
        assertEquals(IndexFreshness(FreshnessState.NOT_BEHIND, scannedAt, same, same, 0, false, null), freshness)
    }

    @Test
    fun `a commit made after the scan is another commit`() {
        val scan = FreshnessFixtures.scanned(root)
        FreshnessFixtures.changed(root.resolve("lib/A.kt"), "class A2\n", scan.minusSeconds(60))
        FreshnessFixtures.commit(root, "second")

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(IndexFreshness(FreshnessState.BEHIND, freshness.scannedAt, other, same, 0, false, null), freshness)
    }

    @Test
    fun `an earlier commit that is checked out is another commit`() {
        Files.writeString(root.resolve("lib/A.kt"), "class A2\n")
        FreshnessFixtures.commit(root, "second")
        FreshnessFixtures.scanned(root)
        TestGit.run(root, listOf("checkout", "-q", "HEAD~1"))

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(FreshnessState.BEHIND, freshness.state)
        assertEquals(other, freshness.commits)
    }

    @Test
    fun `files changed or added after the scan are counted and a change from before the scan is not`() {
        val scan = FreshnessFixtures.scanned(root)
        FreshnessFixtures.changed(root.resolve("lib/A.kt"), "class A2\n", scan.plusSeconds(60))
        FreshnessFixtures.changed(root.resolve("lib/New.kt"), "class New\n", scan.plusSeconds(60))
        FreshnessFixtures.changed(root.resolve("lib/B.kt"), "class B2\n", scan.minusSeconds(60))
        Files.delete(root.resolve("other/C.kt"))

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(IndexFreshness(FreshnessState.BEHIND, freshness.scannedAt, same, other, 2, false, null), freshness)
    }

    @Test
    fun `a file edited after the scan inside a folder that was untracked before it is counted`() {
        val scan = FreshnessFixtures.scanned(root)
        val folder = Files.createDirectories(root.resolve("newsrc"))
        FreshnessFixtures.changed(folder.resolve("A.kt"), "class A\n", scan.minusSeconds(60))
        FreshnessFixtures.changed(folder.resolve("A.kt"), "class A2\n", scan.plusSeconds(60))
        Files.setLastModifiedTime(folder, FileTime.from(scan.minusSeconds(60)))

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(IndexFreshness(FreshnessState.BEHIND, freshness.scannedAt, same, other, 1, false, null), freshness)
    }

    @Test
    fun `without the time of the scan another commit is still seen and changed files cannot be dated`() {
        FreshnessFixtures.scanned(root)
        Files.delete(root.resolve(".kscope/scan.json"))
        val clean = FreshnessFixtures.freshness(root)
        Files.writeString(root.resolve("lib/A.kt"), "class A2\n")
        val edited = FreshnessFixtures.freshness(root)
        FreshnessFixtures.commit(root, "second")
        val committed = FreshnessFixtures.freshness(root)

        val undated = "the time of the scan is not known; the next scan records it"
        assertEquals(IndexFreshness(FreshnessState.NOT_CHECKED, null, same, unchecked, 0, false, undated), clean)
        assertEquals(IndexFreshness(FreshnessState.NOT_CHECKED, null, same, unchecked, 0, false, undated), edited)
        assertEquals(IndexFreshness(FreshnessState.BEHIND, null, other, unchecked, 0, false, undated), committed)
    }

    @Test
    fun `a workspace below the top of its work tree is compared by its own files only`() {
        val workspace = root.resolve("lib")
        val scan = FreshnessFixtures.scanned(workspace)
        FreshnessFixtures.changed(root.resolve("other/C.kt"), "class C2\n", scan.plusSeconds(60))
        val outside = FreshnessFixtures.freshness(workspace)
        FreshnessFixtures.changed(workspace.resolve("B.kt"), "class B2\n", scan.plusSeconds(60))
        val inside = FreshnessFixtures.freshness(workspace)

        assertEquals(FreshnessState.NOT_BEHIND, outside.state)
        assertEquals(IndexFreshness(FreshnessState.BEHIND, inside.scannedAt, same, other, 1, false, null), inside)
    }

    @Test
    fun `a folder that had no commit at the scan has no commit to compare`() {
        val plain = base.resolve("plain")
        FreshnessFixtures.write(plain, "docs/Notes.md", "# Notes\n")
        FreshnessFixtures.scanned(plain)

        val freshness = FreshnessFixtures.freshness(plain)

        assertEquals(none, freshness.commits)
        assertEquals(same, freshness.workingTrees)
    }
}
