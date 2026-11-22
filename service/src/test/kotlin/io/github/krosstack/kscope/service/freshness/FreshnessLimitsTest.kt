package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FreshnessLimitsTest {
    private val base: Path = Files.createTempDirectory("kscope-freshness-limits").toRealPath()
    private val none = FreshnessCounts(0, 0, 0)
    private val same = FreshnessCounts(0, 1, 0)
    private val changed = FreshnessCounts(1, 0, 0)
    private val unchecked = FreshnessCounts(0, 0, 1)

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    private fun plainFolder(): Path {
        val workspace = base.resolve("plain")
        FreshnessFixtures.write(workspace, "docs/Notes.md", "# Notes\n")
        FreshnessFixtures.write(workspace, "build/Output.md", "# Output\n")
        return workspace
    }

    @Test
    fun `a folder without Git is compared by file times and says that this is an estimate`() {
        val workspace = plainFolder()
        val scan = FreshnessFixtures.scanned(workspace)
        val before = FreshnessFixtures.freshness(workspace)
        FreshnessFixtures.changed(workspace.resolve("docs/Notes.md"), "# Changed\n", scan.plusSeconds(60))
        FreshnessFixtures.changed(workspace.resolve("build/Output.md"), "# Changed\n", scan.plusSeconds(60))
        val after = FreshnessFixtures.freshness(workspace)

        assertEquals(IndexFreshness(FreshnessState.NOT_BEHIND, before.scannedAt, none, same, 0, true, null), before)
        assertEquals(IndexFreshness(FreshnessState.BEHIND, after.scannedAt, none, changed, 1, true, null), after)
    }

    @Test
    fun `without the time of the scan a folder without Git cannot be compared`() {
        val workspace = plainFolder()
        FreshnessFixtures.scanned(workspace)
        Files.delete(workspace.resolve(".kscope/scan.json"))

        val freshness = FreshnessFixtures.freshness(workspace)

        val undated = "the time of the scan is not known; the next scan records it"
        assertEquals(IndexFreshness(FreshnessState.NOT_CHECKED, null, none, unchecked, 0, false, undated), freshness)
    }

    @Test
    fun `a check that runs out of time still reads the commit and says that the files were not checked`() {
        assumeTrue(TestGit.available())
        val checkout = FreshnessFixtures.repository(base.resolve("checkout"))
        FreshnessFixtures.scanned(checkout)
        val hurried = OpenOptions(level = ScanLevel.SYNTAX, freshnessLimitMillis = 1)

        val freshness = FreshnessFixtures.freshness(checkout, hurried)

        val late = "this repository could not be checked in 1 ms"
        val expected = IndexFreshness(FreshnessState.NOT_CHECKED, freshness.scannedAt, same, unchecked, 0, false, late)
        assertEquals(expected, freshness)
    }

    @Test
    fun `a checkout that neither its files nor Git can tell about is not checked`() {
        val gone = Checkout(Files.createDirectories(base.resolve("gone")), "abc", tree = true)

        val freshness = FreshnessCheck(60_000, "kscope-no-such-git").of(listOf(gone), null)

        val failed = "this repository could not be compared with the scan"
        val expected = IndexFreshness(FreshnessState.NOT_CHECKED, null, unchecked, unchecked, 0, false, failed)
        assertEquals(expected, freshness)
    }
}
