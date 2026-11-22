package io.github.krosstack.kscope.service.status

import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessFixtures
import io.github.krosstack.kscope.service.freshness.FreshnessState
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkspaceStatusTest {
    private val base: Path = Files.createTempDirectory("kscope-status").toRealPath()
    private val workspace: Path = base.resolve("workspace")

    @BeforeTest
    fun requireGit() {
        assumeTrue(TestGit.available())
    }

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    private fun status(
        limitMillis: Long = STATUS_LIMIT_MILLIS,
        maxFiles: Int = STATUS_MAX_FILES,
        now: () -> Instant = Instant::now,
    ): IndexStatus = StatusFixtures.status(workspace, limitMillis, maxFiles, now)

    private fun present(): IndexStatus.Present = assertIs<IndexStatus.Present>(status())

    @Test
    fun `one repository that did not change is not behind, with its level, scan time and age`() {
        FreshnessFixtures.repository(workspace)
        val scanStart = FreshnessFixtures.scanned(workspace)

        val result = assertIs<IndexStatus.Present>(status(now = { scanStart.plus(Duration.ofHours(3)) }))

        assertEquals(ScanLevel.SYNTAX, result.level)
        assertEquals(1, result.repositories)
        assertEquals(FreshnessState.NOT_BEHIND, result.freshness.state)
        assertEquals(FreshnessCounts(0, 1, 0), result.freshness.commits)
        assertEquals(FreshnessCounts(0, 1, 0), result.freshness.workingTrees)
        assertNotNull(result.scannedAt)
        val age = assertNotNull(result.ageSeconds)
        assertTrue(age in HOURS_3 - ONE_MINUTE..HOURS_3, age.toString())
    }

    @Test
    fun `every working tree is compared, also those of repositories that do not hold the current folder`() {
        val (one, _, three) = FreshnessFixtures.repositories(workspace, 3)
        val scan = FreshnessFixtures.scanned(workspace)
        FreshnessFixtures.changed(one.resolve("lib/A1.kt"), "class Changed\n", scan.plusSeconds(60))
        FreshnessFixtures.changed(three.resolve("lib/New.kt"), "class New\n", scan.plusSeconds(60))

        val freshness = present().freshness

        assertEquals(FreshnessState.BEHIND, freshness.state)
        assertEquals(FreshnessCounts(0, 3, 0), freshness.commits)
        assertEquals(FreshnessCounts(2, 1, 0), freshness.workingTrees)
        assertEquals(2, freshness.changedFiles)
    }

    @Test
    fun `a commit made after the scan makes that repository behind`() {
        val (one, _) = FreshnessFixtures.repositories(workspace, 2)
        FreshnessFixtures.scanned(workspace)
        FreshnessFixtures.write(one, "lib/Later.kt", "class Later\n")
        FreshnessFixtures.commit(one, "second")

        val freshness = present().freshness

        assertEquals(FreshnessState.BEHIND, freshness.state)
        assertEquals(FreshnessCounts(1, 1, 0), freshness.commits)
    }

    @Test
    fun `a folder that Git does not track is walked and compared by file times as an estimate`() {
        FreshnessFixtures.write(workspace, "lib/A.kt", "class A\n")
        val scan = FreshnessFixtures.scanned(workspace)
        FreshnessFixtures.changed(workspace.resolve("lib/A.kt"), "class Changed\n", scan.plusSeconds(60))

        val freshness = present().freshness

        assertEquals(FreshnessState.BEHIND, freshness.state)
        assertTrue(freshness.estimate)
        assertEquals(1, freshness.changedFiles)
    }

    @Test
    fun `a folder that Git does not track and has too many files is not checked, with its own reason`() {
        FreshnessFixtures.write(workspace, "lib/A.kt", "class A\n")
        FreshnessFixtures.write(workspace, "lib/B.kt", "class B\n")
        FreshnessFixtures.write(workspace, "lib/C.kt", "class C\n")
        FreshnessFixtures.scanned(workspace)

        val freshness = assertIs<IndexStatus.Present>(status(maxFiles = 2)).freshness

        assertEquals(FreshnessState.NOT_CHECKED, freshness.state)
        assertEquals(FreshnessCounts(0, 0, 1), freshness.workingTrees)
        assertEquals(0, freshness.changedFiles)
        assertEquals("this folder has more than 2 files and was not walked", freshness.reason)
    }

    @Test
    fun `a check that runs out of time says so and counts nothing`() {
        FreshnessFixtures.repository(workspace)
        FreshnessFixtures.scanned(workspace)

        val freshness = assertIs<IndexStatus.Present>(status(limitMillis = 0)).freshness

        assertEquals(FreshnessState.NOT_CHECKED, freshness.state)
        assertEquals(FreshnessCounts(0, 0, 1), freshness.workingTrees)
        assertEquals("this repository could not be checked in 0 ms", freshness.reason)
    }

    private companion object {
        const val HOURS_3 = 10_800L
        const val ONE_MINUTE = 60L
    }
}
