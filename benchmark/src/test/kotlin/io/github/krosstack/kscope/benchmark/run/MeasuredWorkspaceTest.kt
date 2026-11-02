package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.TemporaryTree
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A `kscope.toml` inside a measured workspace neither changes a benchmark run nor stops it. */
class MeasuredWorkspaceTest {
    private val base: Path = Files.createTempDirectory("kscope-measured")
    private val fixture = Path.of(checkNotNull(System.getProperty("kscope.fixture")))
    private val gold = Path.of(checkNotNull(System.getProperty("kscope.gold")))
    private val workspace: Path = base.resolve("workspace")

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }

    private fun copyFixture() {
        Files.walk(fixture).use { paths ->
            paths.forEach { path ->
                val target = workspace.resolve(fixture.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target)
            }
        }
    }

    private fun measure(): ConformanceResult {
        val scratch = Files.createDirectories(base.resolve("scratch"))
        val result = ConformanceRun(workspace, gold, scratch).run(ScanMode.DEFAULT)
        return result.copy(scan = result.scan.copy(stageMillis = emptyMap(), totalMillis = 0))
    }

    @Test
    fun `the options of a measured workspace carry the fixed settings`() {
        val options = measuredOpenOptions(base.resolve("index"), trustBuild = true)

        assertEquals(WorkspaceSettings.DEFAULTS, options.settings)
        assertEquals(base.resolve("index"), options.indexDirectory)
        assertTrue(options.trustBuild)
        assertEquals(WorkspaceSettings.DEFAULTS, measuredOpenOptions(base.resolve("index")).settings)
    }

    @Test
    fun `a conformance run gives the same result whatever kscope toml the workspace holds`() {
        copyFixture()
        val without = measure()

        Files.writeString(workspace.resolve("kscope.toml"), "[resources]\nworkers = 2\nworker-heap-mib = 512\n")
        val valid = measure()
        Files.writeString(workspace.resolve("kscope.toml"), "[resources]\nworkers = 99\nlevel = 1\n")
        val invalid = measure()

        assertEquals(without, valid)
        assertEquals(without, invalid)
        assertFalse(KrosScope.open(workspace).use { it.info().settings.valid })
    }
}
