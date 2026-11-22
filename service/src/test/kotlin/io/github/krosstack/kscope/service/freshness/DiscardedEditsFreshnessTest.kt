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

class DiscardedEditsFreshnessTest {
    private val base: Path = Files.createTempDirectory("kscope-discarded-edits").toRealPath()
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

    @Test
    fun `edits that were scanned and then discarded make the index behind`() {
        FreshnessFixtures.editedBeforeScan(root, "A")
        FreshnessFixtures.scanned(root)

        TestGit.run(root, listOf("restore", "lib/A.kt"))
        val freshness = FreshnessFixtures.freshness(root)

        assertEquals(IndexFreshness(FreshnessState.BEHIND, freshness.scannedAt, same, other, 1, false, null), freshness)
    }

    @Test
    fun `edits that were scanned and are still there do not make the index behind`() {
        FreshnessFixtures.editedBeforeScan(root, "A")
        FreshnessFixtures.scanned(root)

        val freshness = FreshnessFixtures.freshness(root)

        val expected = IndexFreshness(FreshnessState.NOT_BEHIND, freshness.scannedAt, same, same, 0, false, null)
        assertEquals(expected, freshness)
    }

    @Test
    fun `exactly as many dirty paths as the bound are still recorded as a list`() {
        FreshnessFixtures.editedBeforeScan(root, "A", "B")
        FreshnessFixtures.scannedWithBound(root, 2)

        val recorded = checkNotNull(ScanTimeFile(root.resolve(".kscope")).read()).dirtyPaths

        assertEquals(listOf(DirtyPaths(paths = listOf("lib/A.kt", "lib/B.kt"))), recorded?.values?.toList())
    }

    @Test
    fun `a scan records the dirty paths of each repository in order and a bound records only that it was passed`() {
        FreshnessFixtures.editedBeforeScan(root, "B", "A")
        FreshnessFixtures.scanned(root)
        val recorded = checkNotNull(ScanTimeFile(root.resolve(".kscope")).read()).dirtyPaths
        FreshnessFixtures.scannedWithBound(root, 1)
        val bounded = checkNotNull(ScanTimeFile(root.resolve(".kscope")).read()).dirtyPaths

        assertEquals(listOf(DirtyPaths(paths = listOf("lib/A.kt", "lib/B.kt"))), recorded?.values?.toList())
        assertEquals(listOf(DirtyPaths(exceeded = true)), bounded?.values?.toList())
    }
}
