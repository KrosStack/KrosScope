package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UnrecordedChangesFreshnessTest {
    private val base: Path = Files.createTempDirectory("kscope-unrecorded-changes").toRealPath()
    private val root: Path = base.resolve("checkout")
    private val same = FreshnessCounts(0, 1, 0)
    private val other = FreshnessCounts(1, 0, 0)
    private val unchecked = FreshnessCounts(0, 0, 1)
    private val notRecorded = "the scan did not record its changed files; the next scan records them"
    private val tooMany = "the scan found too many changed files to record them"

    @BeforeTest
    fun createCheckout() {
        assumeTrue(TestGit.available())
        FreshnessFixtures.repository(root)
    }

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    private fun notChecked(freshness: IndexFreshness, reason: String) =
        IndexFreshness(FreshnessState.NOT_CHECKED, freshness.scannedAt, same, unchecked, 0, false, reason)

    private fun behind(freshness: IndexFreshness, reason: String) =
        IndexFreshness(FreshnessState.BEHIND, freshness.scannedAt, same, other, 1, false, reason)

    @Test
    fun `a scan that found more changed files than its bound did not record them and the index may be behind`() {
        FreshnessFixtures.editedBeforeScan(root, "A", "B")
        FreshnessFixtures.scannedWithBound(root, 1)

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(notChecked(freshness, tooMany), freshness)
    }

    @Test
    fun `a scan file from before the changed files were recorded says that the index may be behind`() {
        FreshnessFixtures.editedBeforeScan(root, "A")
        FreshnessFixtures.scannedWithoutList(root)

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(notChecked(freshness, notRecorded), freshness)
    }

    @Test
    fun `a file changed after a scan that passed its bound is behind and the count is a lower bound`() {
        FreshnessFixtures.editedBeforeScan(root, "A", "B")
        val started = FreshnessFixtures.scannedWithBound(root, 1)
        FreshnessFixtures.changed(root.resolve("lib/B.kt"), "class B3\n", started.plusSeconds(60))

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(behind(freshness, tooMany), freshness)
    }

    @Test
    fun `a file changed after a scan file without the list is behind and the count is a lower bound`() {
        FreshnessFixtures.editedBeforeScan(root, "A")
        val started = FreshnessFixtures.scannedWithoutList(root)
        FreshnessFixtures.changed(root.resolve("lib/A.kt"), "class A3\n", started.plusSeconds(60))

        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(behind(freshness, notRecorded), freshness)
    }
}
