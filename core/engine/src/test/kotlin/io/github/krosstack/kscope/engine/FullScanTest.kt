package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.model.DiagnosticSeverity
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullScanTest {
    @Test
    fun `a first scan inventories every file and counts the artifacts by type`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine().use { engine ->
                val report = engine.update()
                assertEquals(19, report.artifacts)
                val byType = engine.graph().coverage().artifactsByType
                assertEquals(6L, byType["kotlin.source"])
                assertEquals(3L, byType["gradle.build-script"])
                assertEquals(2L, byType["markdown"])
                assertEquals(1L, byType["gradle.settings"])
                assertEquals(3L, byType["unknown"])
                assertEquals(19L, byType.values.sum())
            }
        }

    @Test
    fun `the first scan produces the discovery and build model units and skips none`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.engine().use { engine ->
                val report = engine.update()
                assertEquals(2, report.unitsProduced)
                assertEquals(0, report.unitsSkipped)
                assertEquals(0, report.unitsRemoved)
                val producers = engine.store.units().map { it.producer.id }
                assertEquals(listOf("kscope.build.structural", "kscope.discovery"), producers)
            }
        }

    @Test
    fun `an unchanged second scan skips every unit`() = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engine().use { engine ->
            val dump = engine.also { it.update() }.canonicalDump()
            val report = engine.update()
            assertEquals(0, report.unitsProduced)
            assertEquals(2, report.unitsSkipped)
            assertEquals(dump, engine.canonicalDump())
        }
    }

    @Test
    fun `a reopened index is current without any work`() = TestWorkspace("gradle-multi").use { workspace ->
        workspace.engine().use { it.update() }
        workspace.engine().use { engine ->
            val report = engine.update()
            assertEquals(0, report.unitsProduced)
            assertEquals(2, report.unitsSkipped)
        }
    }

    @Test
    fun `files above the size limit are inventoried but flagged and not analysed`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.write("big.kt", "val x = 1\n".repeat(50))
            workspace.engine(maxArtifactBytes = 300).use { engine ->
                val report = engine.update()
                val flagged = report.diagnostics.filter { it.code.value == "discovery.artifact-too-large" }
                assertTrue(flagged.single().message.startsWith("big.kt "))
                assertEquals(DiagnosticSeverity.INFO, flagged.single().severity)
                assertTrue(engine.graph().artifacts().any { it.ref.path.value == "big.kt" })
            }
        }

    @Test
    fun `oversized files are never handed to producers`() = TestWorkspace("gradle-multi").use { workspace ->
        workspace.write("big.kt", "val x = 1\n".repeat(50))
        val producer = RecordingProducer("recorder")
        workspace.engine(coreRegistry(producer), maxArtifactBytes = 300).use { it.update() }
        assertFalse(producer.producedKeys.any { it.endsWith("big.kt") })
        assertTrue(producer.producedKeys.any { it.endsWith("Greeter.kt") })
    }

    @Test
    fun `line counts include an unterminated last line`() = TestWorkspace().use { workspace ->
        workspace.write("a.kt", "one\ntwo\nthree")
        workspace.write("b.kt", "one\ntwo\n")
        workspace.write("c.kt", "")
        workspace.engine().use { engine ->
            engine.update()
            val lines = engine.graph().artifacts().associate { it.ref.path.value to it.lineCount }
            assertEquals(mapOf("a.kt" to 3, "b.kt" to 2, "c.kt" to 0), lines)
        }
    }

    @Test
    fun `an index inside the workspace is not inventoried`() = TestWorkspace("plain").use { workspace ->
        val engine = ScanEngine(coreRegistry(), WorkspaceConfig(root = workspace.root))
        engine.use {
            it.update()
            assertTrue(Files.isRegularFile(workspace.root.resolve(".kscope/index.db")))
            val report = it.update()
            assertEquals(0, report.unitsProduced)
            assertTrue(it.graph().artifacts().none { artifact -> artifact.ref.path.value.startsWith(".kscope") })
        }
    }

    @Test
    fun `stage timings are reported for every stage that ran`() = TestWorkspace("plain").use { workspace ->
        workspace.engine().use { engine ->
            val stages = engine.update().stageMillis.keys
            val expected = listOf("discovery", "build-model", "source-sets", "synthesis") +
                listOf("api-digests", "production", "linking", "cleanup", "search-index")
            assertEquals(expected, stages.toList())
        }
    }
}
