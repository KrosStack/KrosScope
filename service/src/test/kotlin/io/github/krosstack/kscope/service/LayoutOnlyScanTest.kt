package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.analyzer.kotlin.KotlinAnalyzerProducer
import io.github.krosstack.kscope.engine.CoreCapabilities
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.spi.CapabilityRegistry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A model inferred from the directory layout alone knows no dependencies, so the analyzer has to assume them. */
class LayoutOnlyScanTest {
    private val sample = SampleWorkspace("mixed")
    private val index: Path = Files.createTempDirectory("kscope-layout-index")

    @AfterTest
    fun cleanUp() {
        sample.close()
        index.toFile().deleteRecursively()
    }

    private fun engine(): ScanEngine {
        val registry = CapabilityRegistry(
            detectors = CoreCapabilities.detectors(),
            buildModelProviders = CoreCapabilities.buildModelProviders(),
            producers = listOf(KotlinAnalyzerProducer()),
        )
        return ScanEngine(registry, WorkspaceConfig(sample.root, index, trusted = false))
    }

    @Test
    fun `the assumption is announced and any source change redoes every source set`() {
        engine().use { engine ->
            val first = engine.update()
            assertTrue(first.diagnostics.any { it.code.value == "kotlin.dependencies-assumed" }, "${first.diagnostics}")
            assertTrue(first.diagnostics.none { it.code.value == "kotlin.cyclic-module-reference" })

            sample.write("app/src/main/kotlin/mixed/app/Main.kt", "package mixed.app\n\nfun main() = Unit\n")
            val changed = engine.update()
            assertEquals(1 + 4, changed.unitsProduced, "Discovery and all four source sets")
        }
    }
}
