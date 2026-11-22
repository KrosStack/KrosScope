package io.github.krosstack.kscope.service.status

import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.freshness.FreshnessCounts
import io.github.krosstack.kscope.service.freshness.FreshnessFixtures
import io.github.krosstack.kscope.service.freshness.FreshnessState
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndexStatusKindsTest {
    private val base: Path = Files.createTempDirectory("kscope-status-kinds").toRealPath()
    private val workspace: Path = base.resolve("workspace")

    @BeforeTest
    fun requireGit() {
        assumeTrue(TestGit.available())
    }

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(base)
    }

    @Test
    fun `a workspace that was never scanned has no index and nothing is created`() {
        FreshnessFixtures.repository(workspace)

        assertEquals(IndexStatus.NoIndex, StatusFixtures.status(workspace))
        assertTrue(Files.notExists(workspace.resolve(".kscope")))
    }

    @Test
    fun `an index of another version is named, and neither replaced nor changed`() {
        FreshnessFixtures.repository(workspace)
        FreshnessFixtures.scanned(workspace)
        RandomAccessFile(workspace.resolve(".kscope/index.db").toFile(), "rw").use {
            it.seek(USER_VERSION_OFFSET)
            it.writeInt(1)
        }
        val before = StatusFixtures.indexFiles(workspace)

        val result = StatusFixtures.status(workspace)

        assertEquals(1, assertIs<IndexStatus.OtherFormat>(result).found)
        assertEquals(before, StatusFixtures.indexFiles(workspace))
    }

    @Test
    fun `a file that is not a database is damaged`() {
        Files.createDirectories(workspace.resolve(".kscope"))
        Files.writeString(workspace.resolve(".kscope/index.db"), "not a database")

        assertIs<IndexStatus.Damaged>(StatusFixtures.status(workspace))
    }

    @Test
    fun `an index without the times of its scan is compared by commit, and a clean working tree is not checked`() {
        FreshnessFixtures.repository(workspace)
        FreshnessFixtures.scanned(workspace)
        Files.delete(workspace.resolve(".kscope/scan.json"))

        val result = assertIs<IndexStatus.Present>(StatusFixtures.status(workspace))

        assertNull(result.scannedAt)
        assertNull(result.ageSeconds)
        assertEquals(FreshnessState.NOT_CHECKED, result.freshness.state)
        assertEquals(FreshnessCounts(0, 1, 0), result.freshness.commits)
        assertEquals(FreshnessCounts(0, 0, 1), result.freshness.workingTrees)
        assertEquals("the time of the scan is not known; the next scan records it", result.freshness.reason)
    }

    @Test
    fun `an index without the times of its scan cannot say whether a changed working tree is behind`() {
        FreshnessFixtures.repository(workspace)
        FreshnessFixtures.scanned(workspace)
        Files.delete(workspace.resolve(".kscope/scan.json"))
        FreshnessFixtures.write(workspace, "lib/New.kt", "class New\n")

        val freshness = assertIs<IndexStatus.Present>(StatusFixtures.status(workspace)).freshness

        assertEquals(FreshnessState.NOT_CHECKED, freshness.state)
        assertEquals(FreshnessCounts(0, 0, 1), freshness.workingTrees)
        assertEquals("the time of the scan is not known; the next scan records it", freshness.reason)
    }

    @Test
    fun `asking changes no file of the index and writes no server file`() {
        FreshnessFixtures.repository(workspace)
        FreshnessFixtures.scanned(workspace)
        val before = StatusFixtures.indexFiles(workspace)

        StatusFixtures.status(workspace)

        assertEquals(before, StatusFixtures.indexFiles(workspace))
        assertTrue(Files.notExists(workspace.resolve(".kscope/serve.json")))
    }

    private companion object {
        const val USER_VERSION_OFFSET = 60L
    }
}
