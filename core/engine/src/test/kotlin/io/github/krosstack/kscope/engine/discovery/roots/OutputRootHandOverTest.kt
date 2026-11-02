package io.github.krosstack.kscope.engine.discovery.roots

import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.artifactPaths
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.model.GeneratedInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutputRootHandOverTest {
    @Test
    fun `output files are handed to producers and the files of generated roots are still withheld`() {
        val producer = RecordingProducer("by-artifact")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs()
            workspace.writeOutputs()
            workspace.write(GENERATED_FILE, "package com.example.app\n\nclass Generated\n")
            workspace.engine(outputRegistry(producer)).use { engine ->
                engine.update()
                assertTrue(GENERATED_FILE in engine.artifactPaths())
                assertTrue(OUTPUT_FILE in producer.contextPaths)
                assertTrue(GENERATED_FILE !in producer.contextPaths)
                assertTrue(producer.producedKeys.any { it.endsWith(":$OUTPUT_FILE") })
                assertTrue(producer.producedKeys.none { "Generated.kt" in it })
            }
        }
    }

    @Test
    fun `a file that the scope marks generated keeps that mark and is withheld`() {
        val producer = RecordingProducer("by-artifact")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs()
            workspace.writeOutputs()
            val scope = { ScanScope(generated = listOf("**/dist/**")) }
            workspace.engine(outputRegistry(producer), scope = scope).use { engine ->
                engine.update()
                val output = engine.graph().artifacts().single { it.ref.path.value == OUTPUT_FILE }
                assertEquals(GeneratedInfo("settings", null), output.generated)
                assertTrue(OUTPUT_FILE !in producer.contextPaths)
            }
        }
    }

    @Test
    fun `an output root that appears after a scan is read by the next scan`() {
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs()
            workspace.engine(outputRegistry()).use { engine ->
                engine.update()
                assertTrue(OUTPUT_FILE !in engine.artifactPaths())
                workspace.writeOutputs()
                engine.update()
                assertTrue(OUTPUT_FILE in engine.artifactPaths())
            }
        }
    }

    @Test
    fun `a rebuilt output file changes the inventory and reruns the units that consume it, and verify agrees`() {
        val producer = RecordingProducer("by-artifact")
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.declareOutputs()
            workspace.writeOutputs()
            workspace.engine(outputRegistry(producer)).use { engine ->
                engine.update()
                producer.calls.clear()
                workspace.write(OUTPUT_FILE, "package com.example.app\n\nclass Out2\n")
                val report = engine.update()
                assertEquals(2, report.unitsProduced, "The inventory and the unit of the output file are rebuilt")
                assertTrue(producer.producedKeys.single().endsWith(":$OUTPUT_FILE"))
                engine.assertMatchesRebuild()
            }
        }
    }
}
