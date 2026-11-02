package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.engine.workspaceStamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnalyzerRecordingTest {
    private val recorded = "kscope.analyzers"

    private fun TestWorkspace.engineWith(disabled: () -> Set<String>) = ScanEngine(
        coreRegistry(),
        WorkspaceConfig(root, indexDirectory, disabledAnalyzers = disabled),
    ).also { seedRepositoryId("fixed-repository-id") }

    @Test
    fun `the analyzers unit exists only while a group is switched off and its key follows the names`() =
        TestWorkspace("gradle-multi").use { workspace ->
            var disabled = emptySet<String>()
            workspace.engineWith { disabled }.use { engine ->
                engine.update()
                assertTrue(engine.store.units().none { it.producer.id == recorded })

                disabled = setOf("aidl")
                engine.update()
                val key = engine.store.units().single { it.producer.id == recorded }.fingerprint

                disabled = setOf("aidl", "schemas")
                engine.update()
                val both = engine.store.units().single { it.producer.id == recorded }.fingerprint
                assertNotEquals(key, both)

                disabled = linkedSetOf("schemas", "aidl")
                val rescan = engine.update()
                assertEquals(0, rescan.unitsProduced)
                assertEquals(both, engine.store.units().single { it.producer.id == recorded }.fingerprint)

                disabled = emptySet()
                engine.update()
                assertTrue(engine.store.units().none { it.producer.id == recorded })
            }
        }

    @Test
    fun `the index says whether it was built without a set of groups`() =
        TestWorkspace("gradle-multi").use { workspace ->
            var disabled = setOf("aidl")
            workspace.engineWith { disabled }.use { engine ->
                engine.update()
                assertTrue(engine.isIndexedWithout(setOf("aidl")))
                assertTrue(!engine.isIndexedWithout(emptySet()))
                assertTrue(!engine.isIndexedWithout(setOf("aidl", "schemas")))
                disabled = emptySet()
                engine.update()
                assertTrue(engine.isIndexedWithout(emptySet()))
            }
        }

    @Test
    fun `the stamp of the workspace changes with the switches`() = TestWorkspace("gradle-multi").use { workspace ->
        var disabled = emptySet<String>()
        workspace.engineWith { disabled }.use { engine ->
            val all = engine.workspaceStamp()
            disabled = setOf("aidl")
            assertNotEquals(all, engine.workspaceStamp())
            disabled = emptySet()
            assertEquals(all, engine.workspaceStamp())
        }
    }

    @Test
    fun `a scan asks for the registry once and uses it from start to end`() =
        TestWorkspace("gradle-multi").use { workspace ->
            var asked = 0
            val engine = ScanEngine(
                { coreRegistry().also { asked++ } },
                WorkspaceConfig(workspace.root, workspace.indexDirectory),
            )
            engine.use {
                it.update()
                assertEquals(1, asked)
                it.update()
                assertEquals(2, asked)
            }
        }
}
