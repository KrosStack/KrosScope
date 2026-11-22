package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.DEFAULT_FRESHNESS_LIMIT_MILLIS
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceOfRepositoriesFreshnessTest {
    private val base: Path = Files.createTempDirectory("kscope-repositories-freshness").toRealPath()
    private val workspace: Path = base.resolve("workspace")

    @BeforeTest
    fun requireGit() {
        assumeTrue(TestGit.available())
    }

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    private fun withoutGit(): IndexFreshness = KrosScope.open(workspace, FreshnessFixtures.PATIENT).use {
        val times = ScanTimeFile(workspace.resolve(".kscope")).read()
        FreshnessCheck(DEFAULT_FRESHNESS_LIMIT_MILLIS, "kscope-no-such-git").of(it.checkouts(null), times)
    }

    @Test
    fun `asked from the root thirty-one repositories are compared by their commits without any process`() {
        val repositories = FreshnessFixtures.repositories(workspace, MANY)
        val scan = FreshnessFixtures.scanned(workspace)
        FreshnessFixtures.changed(repositories.first().resolve("lib/A1.kt"), "class Changed\n", scan.plusSeconds(60))
        val before = withoutGit()
        FreshnessFixtures.commit(repositories.first(), "second")
        TestGit.run(repositories.last(), listOf("pack-refs", "--all"))
        val after = withoutGit()

        val commits = FreshnessCounts(0, MANY, 0)
        val trees = FreshnessCounts(0, 0, MANY)
        val same = IndexFreshness(FreshnessState.NOT_BEHIND, before.scannedAt, commits, trees, 0, false, null)
        assertEquals(same, before)
        assertEquals(same.copy(state = FreshnessState.BEHIND, commits = FreshnessCounts(1, MANY - 1, 0)), after)
    }

    @Test
    fun `asked from inside a repository only the working tree of that repository is compared`() {
        val (one, two) = FreshnessFixtures.repositories(workspace, 2)
        val scan = FreshnessFixtures.scanned(workspace)
        FreshnessFixtures.changed(one.resolve("lib/A1.kt"), "class Changed\n", scan.plusSeconds(60))
        FreshnessFixtures.changed(two.resolve("lib/A2.kt"), "class Changed\n", scan.plusSeconds(60))

        val fromRoot = FreshnessFixtures.freshness(workspace)
        val fromOne = FreshnessFixtures.freshness(workspace, folder = "r01/lib")

        val commits = FreshnessCounts(0, 2, 0)
        val trees = FreshnessCounts(0, 0, 2)
        val expected = IndexFreshness(FreshnessState.NOT_BEHIND, fromRoot.scannedAt, commits, trees, 0, false, null)
        assertEquals(expected, fromRoot)
        val behind = expected.copy(FreshnessState.BEHIND, workingTrees = FreshnessCounts(1, 0, 1), changedFiles = 1)
        assertEquals(behind, fromOne)
    }

    @Test
    fun `a layout that the files do not explain is asked from Git and is not checked when Git cannot be asked`() {
        val (one, _) = FreshnessFixtures.repositories(workspace, 2)
        FreshnessFixtures.scanned(workspace)
        val branch = TestGit.run(one, listOf("symbolic-ref", "HEAD"))
        TestGit.run(one, listOf("symbolic-ref", "refs/heads/alias", branch))
        TestGit.run(one, listOf("symbolic-ref", "HEAD", "refs/heads/alias"))

        val asked = FreshnessFixtures.freshness(workspace)
        val unasked = withoutGit()

        assertEquals(FreshnessState.NOT_BEHIND, asked.state)
        assertEquals(FreshnessCounts(0, 2, 0), asked.commits)
        assertEquals(FreshnessState.NOT_BEHIND, unasked.state)
        assertEquals(FreshnessCounts(0, 1, 1), unasked.commits)
    }

    @Test
    fun `a repository with a reftable layout is asked from Git and its reference files are not read`() {
        val (one, _) = FreshnessFixtures.repositories(workspace, 2)
        FreshnessFixtures.scanned(workspace)
        Files.createDirectories(one.resolve(".git/reftable"))

        val asked = FreshnessFixtures.freshness(workspace)
        val unasked = withoutGit()

        assertEquals(FreshnessCounts(0, 2, 0), asked.commits)
        assertEquals(FreshnessCounts(0, 1, 1), unasked.commits)
    }

    private companion object {
        const val MANY = 31
    }
}
