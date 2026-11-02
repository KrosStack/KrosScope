package io.github.krosstack.kscope.engine.discovery.roots

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.engine.structure.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutputRootInventoryTest {
    @Test
    fun `output that git ignores is inventoried from disk and labelled with the generator of its root`() {
        assumeTrue(TestGit.available())
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs()
            workspace.commitWithIgnoredOutput()
            workspace.writeOutputs()
            workspace.engine(outputRegistry()).use { engine ->
                engine.update()
                val artifacts = engine.graph().artifacts().associateBy { it.ref.path.value }
                val output = assertNotNull(artifacts[OUTPUT_FILE])
                assertEquals(OUTPUT_LABEL, output.generated)
                assertEquals("kotlin.source", output.type.id)
                assertEquals(OUTPUT_LABEL, artifacts.getValue(OUTPUT_NOTES).generated)
                assertNull(artifacts.getValue(APP_MAIN).generated)
                assertNull(artifacts.getValue(OUTPUTS_FILE).generated)
            }
        }
    }

    @Test
    fun `a file that the listing already finds below an output root is labelled as well`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs()
            workspace.writeOutputs()
            workspace.engine(outputRegistry()).use { engine ->
                engine.update()
                val artifacts = engine.graph().artifacts().associateBy { it.ref.path.value }
                assertEquals(OUTPUT_LABEL, artifacts.getValue(OUTPUT_FILE).generated)
                assertNull(artifacts.getValue(APP_MAIN).generated)
            }
        }
    }

    @Test
    fun `a missing output directory adds nothing`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs()
            workspace.engine(outputRegistry()).use { engine ->
                val report = engine.update()
                assertTrue(engine.graph().artifacts().none { it.generated != null })
                assertTrue(report.diagnostics.none { OUTPUT_DIRECTORY in it.message })
            }
        }
    }

    @Test
    fun `the repository root is no output root`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs("/ bundler\n")
            workspace.engine(outputRegistry()).use { engine ->
                engine.update()
                assertTrue(engine.graph().artifacts().none { it.generated != null })
            }
        }
    }

    @Test
    fun `exclude and include act on the files of output roots too`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs()
            workspace.writeOutputs()
            workspace.engine(outputRegistry(), scope = { ScanScope(exclude = listOf("**/dist/**")) }).use { engine ->
                engine.update()
                assertTrue(engine.artifactPaths().none { "/dist/" in it })
            }
            workspace.engine(outputRegistry(), scope = { ScanScope(include = listOf("core/**")) }).use { engine ->
                engine.update()
                assertTrue(engine.artifactPaths().none { "/dist/" in it })
            }
        }
    }
}
