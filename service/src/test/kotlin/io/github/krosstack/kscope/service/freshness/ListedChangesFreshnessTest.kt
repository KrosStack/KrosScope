package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ListedChangesFreshnessTest {
    private val base: Path = Files.createTempDirectory("kscope-listed-changes").toRealPath()
    private val root: Path = base.resolve("checkout")
    private val same = FreshnessCounts(0, 1, 0)
    private val other = FreshnessCounts(1, 0, 0)

    @BeforeTest
    fun createCheckout() {
        assumeTrue(TestGit.available())
        FreshnessFixtures.repository(root)
    }

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    private fun behind(freshness: IndexFreshness, files: Int) =
        IndexFreshness(FreshnessState.BEHIND, freshness.scannedAt, same, other, files, false, null)

    @Test
    fun `a path that was dirty at the scan and was edited again after it is behind`() {
        FreshnessFixtures.editedBeforeScan(root, "A")
        val started = FreshnessFixtures.scanned(root)
        FreshnessFixtures.changed(root.resolve("lib/A.kt"), "class A3\n", started.plusSeconds(60))

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(behind(freshness, 1), freshness)
    }

    @Test
    fun `one restored path and one edited again are both counted`() {
        FreshnessFixtures.editedBeforeScan(root, "A", "B")
        val started = FreshnessFixtures.scanned(root)
        TestGit.run(root, listOf("restore", "lib/A.kt"))
        FreshnessFixtures.changed(root.resolve("lib/B.kt"), "class B3\n", started.plusSeconds(60))

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(behind(freshness, 2), freshness)
    }

    @Test
    fun `an untracked file that was scanned and deleted after the scan is behind`() {
        val file = FreshnessFixtures.changed(root.resolve("lib/New.kt"), "class New\n", Instant.now().minusSeconds(60))
        FreshnessFixtures.scanned(root)
        Files.delete(file)

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(behind(freshness, 1), freshness)
    }

    @Test
    fun `a rename that was scanned and undone after the scan counts the old and the new path`() {
        TestGit.run(root, listOf("mv", "lib/A.kt", "lib/Moved.kt"))
        FreshnessFixtures.scanned(root)
        TestGit.run(root, listOf("mv", "lib/Moved.kt", "lib/A.kt"))

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(behind(freshness, 2), freshness)
    }
}
