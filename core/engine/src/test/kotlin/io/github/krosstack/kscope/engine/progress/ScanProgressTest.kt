package io.github.krosstack.kscope.engine.progress

import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.canonicalDump
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.model.OwnershipKind
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanProgressTest {
    private val events = CopyOnWriteArrayList<ProgressEvent>()
    private val listener = ProgressListener { events += it }
    private val stages =
        listOf("discovery", "build-model", "source-sets", "synthesis", "production", "linking", "search-index")

    private fun producer() = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)

    private fun observed(workspace: TestWorkspace): ScanEngine =
        ScanEngine(coreRegistry(producer()), WorkspaceConfig(workspace.root, workspace.indexDirectory), listener)

    private fun started(): List<String> = events.filterIsInstance<ProgressEvent.Started>().map { it.stage.id }

    private fun advances(stage: ProgressStage): List<ProgressEvent.Advanced> =
        events.filterIsInstance<ProgressEvent.Advanced>().filter { it.stage == stage }

    @Test
    fun `a scan reports its stages in the order they run and finishes each one`() =
        TestWorkspace("gradle-multi").use { workspace ->
            observed(workspace).use { it.update() }

            assertEquals(stages, started())
            assertEquals(stages, events.filterIsInstance<ProgressEvent.Finished>().map { it.stage.id })
        }

    @Test
    fun `discovery counts the files and the build model counts the source sets`() =
        TestWorkspace("gradle-multi").use { workspace ->
            observed(workspace).use { it.update() }

            val files = advances(ScanStages.DISCOVERY).single()
            assertEquals(ProgressEvent.Advanced(ScanStages.DISCOVERY, 19, 19, "files"), files)
            assertEquals("source sets", advances(ScanStages.BUILD_MODEL).single().unit)
        }

    @Test
    fun `production counts the units it has done out of the units it has to do`() =
        TestWorkspace("gradle-multi").use { workspace ->
            observed(workspace).use { it.update() }

            val counts = advances(ScanStages.PRODUCTION)
            val total = counts.first().total
            assertTrue(counts.size >= 2, counts.toString())
            assertEquals(0L, counts.first().done)
            assertTrue(total != null && total > 0, counts.toString())
            assertEquals(total, counts.last().done)
            assertTrue(counts.zipWithNext().all { (before, after) -> before.done <= after.done }, counts.toString())
            assertTrue(counts.all { it.unit == "units" && it.total == total })
        }

    @Test
    fun `a scan that has nothing to redo reports zero units of zero`() =
        TestWorkspace("gradle-multi").use { workspace ->
            observed(workspace).use { engine ->
                engine.update()
                events.clear()
                engine.update()

                val counts = advances(ScanStages.PRODUCTION).single()
                assertEquals(0L, counts.done)
                assertEquals(0L, counts.total)
            }
        }

    @Test
    fun `verification reports the clean rebuild under its own stage names`() =
        TestWorkspace("gradle-multi").use { workspace ->
            observed(workspace).use { it.update(verify = true) }

            val verification = started().filter { it.startsWith(ScanStages.VERIFY_PREFIX) }
            assertEquals(stages, verification.map { it.removePrefix(ScanStages.VERIFY_PREFIX) })
            assertEquals(stages.size * 2, started().size)
        }

    @Test
    fun `listening changes nothing about the result`() {
        val withListener = scanned { workspace -> observed(workspace) }
        assertTrue(events.isNotEmpty())
        val silent = scanned { workspace -> workspace.engine(coreRegistry(producer())) }

        assertEquals(silent.first, withListener.first)
        assertEquals(silent.second, withListener.second)
    }

    private fun scanned(engineOf: (TestWorkspace) -> ScanEngine) = TestWorkspace("gradle-multi").use { workspace ->
        workspace.seedRepositoryId("fixed-repository")
        engineOf(workspace).use { it.update().copy(stageMillis = emptyMap()) to it.canonicalDump() }
    }
}
