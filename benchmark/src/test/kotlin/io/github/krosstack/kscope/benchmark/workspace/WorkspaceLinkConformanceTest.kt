package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.benchmark.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceLinkConformanceTest {
    private val gold = WorkspaceGoldLoader.load(goldDirectory)

    @Test
    fun `every relationship class of the gold is scored`() {
        assertEquals(11, gold.meta.classDefinitions.size)
        assertEquals(gold.meta.classDefinitions.keys.sorted(), result.classes.map { it.name })
        assertEquals(gold.meta.counts, result.classes.associate { it.name to it.goldCount })
    }

    @Test
    fun `every gold relationship is found as a link between the repositories`() {
        result.classes.forEach {
            assertEquals(it.goldCount, it.found, "${it.name}: ${it.missing}")
            assertEquals(1.0, it.recall, it.name)
        }
    }

    @Test
    fun `every link that is found carries the expected evidence class`() {
        result.classes.forEach {
            assertEquals(emptyList(), it.evidenceMismatches, it.name)
            assertEquals(it.found, it.withExpectedEvidence, it.name)
        }
    }

    @Test
    fun `the gate is met and only the class that needs a build is scored on the second scan`() {
        val onOutputs = result.classes.filter { it.scan == ScanPhase.BUILD_OUTPUTS.label }.map { it.name }

        assertTrue(result.passed)
        assertEquals(listOf("bundle_file_to_source"), onOutputs)
        assertEquals(6, result.repositories.size)
    }

    @Test
    fun `a class with a missing relationship or a wrong evidence class does not meet the gate`() {
        val met = result.classes.first()

        assertFalse(met.copy(found = met.goldCount - 1, recall = 0.5, missing = listOf("x")).met)
        assertFalse(met.copy(evidenceMismatches = listOf("x")).met)
        assertFalse(WorkspaceConformanceResult(result.repositories, emptyList()).passed)
        assertFalse(WorkspaceConformanceResult(result.repositories, result.classes + met.copy(recall = 0.0)).passed)
    }

    private companion object {
        val goldDirectory: Path = Path.of(checkNotNull(System.getProperty("kscope.workspaceGold")))

        val result: WorkspaceConformanceResult by lazy {
            val scratch = Files.createTempDirectory("kscope-workspace-conformance")
            try {
                val fixture = Path.of(checkNotNull(System.getProperty("kscope.workspaceFixture")))
                WorkspaceConformanceRun(fixture, goldDirectory, scratch).run()
            } finally {
                TemporaryTree.delete(scratch)
            }
        }
    }
}
