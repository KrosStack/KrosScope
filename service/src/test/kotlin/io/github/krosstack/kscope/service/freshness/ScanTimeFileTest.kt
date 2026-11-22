package io.github.krosstack.kscope.service.freshness

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.settings.InvalidSettingsException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanTimeFileTest {
    private val base: Path = Files.createTempDirectory("kscope-scan-time").toRealPath()

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }

    @Test
    fun `the times of a scan are read back as they were written and replace the earlier ones`() {
        val file = ScanTimeFile(base)
        val started = Instant.parse("2026-01-02T03:04:05.006Z")

        file.write(started.minusSeconds(100), started.minusSeconds(90))
        file.write(started, started.plusSeconds(7))

        assertEquals(ScanTimes(1, "2026-01-02T03:04:05.006Z", "2026-01-02T03:04:12.006Z"), file.read())
        val names = Files.list(base).use { files -> files.map { it.fileName.toString() }.toList() }
        assertEquals(listOf("scan.json"), names)
    }

    @Test
    fun `a file of the first version without the changed files is read with no list`() {
        val old = "{\"format\":1,\"startedAt\":\"2026-01-02T03:04:05Z\",\"finishedAt\":\"2026-01-02T03:04:06Z\"}"
        Files.writeString(base.resolve("scan.json"), old)

        val times = assertNotNull(ScanTimeFile(base).read())

        assertEquals(ScanTimes(1, "2026-01-02T03:04:05Z", "2026-01-02T03:04:06Z", null), times)
    }

    @Test
    fun `the changed files of a scan are read back as they were written`() {
        val file = ScanTimeFile(base)
        val dirty = mapOf("a" to DirtyPaths(paths = listOf("x/Y.kt")), "b" to DirtyPaths(exceeded = true))

        file.write(Instant.parse("2026-01-02T03:04:05Z"), Instant.parse("2026-01-02T03:04:06Z"), dirty)

        assertEquals(dirty, assertNotNull(file.read()).dirtyPaths)
    }

    @Test
    fun `a missing or damaged file and a file of another format give no times`() {
        val file = ScanTimeFile(base)
        val texts = listOf(
            "",
            "{",
            "{\"format\":2,\"startedAt\":\"2026-01-02T03:04:05Z\",\"finishedAt\":\"2026-01-02T03:04:06Z\"}",
            "{\"format\":1,\"startedAt\":\"yesterday\",\"finishedAt\":\"2026-01-02T03:04:06Z\"}",
            "{\"format\":1,\"startedAt\":\"2026-01-02T03:04:07Z\",\"finishedAt\":\"2026-01-02T03:04:06Z\"}",
        )

        assertNull(file.read())
        texts.forEach { text ->
            Files.writeString(base.resolve("scan.json"), text)
            assertNull(file.read(), text)
        }
    }

    @Test
    fun `a scan records its times and a refused scan leaves them as they were`() {
        val workspace = Files.createDirectories(base.resolve("workspace"))
        FreshnessFixtures.write(workspace, "Notes.md", "# Notes\n")
        val before = Instant.now()

        FreshnessFixtures.scanned(workspace)
        val recorded = assertNotNull(ScanTimeFile(workspace.resolve(".kscope")).read())
        FreshnessFixtures.write(workspace, "kscope.toml", "level = 7\n")
        assertFailsWith<InvalidSettingsException> { KrosScope.open(workspace).use { it.scan() } }

        assertTrue(Instant.parse(recorded.startedAt) >= before.minusSeconds(1))
        assertTrue(Instant.parse(recorded.finishedAt) <= Instant.now().plusSeconds(1))
        assertEquals(recorded, ScanTimeFile(workspace.resolve(".kscope")).read())
    }
}
