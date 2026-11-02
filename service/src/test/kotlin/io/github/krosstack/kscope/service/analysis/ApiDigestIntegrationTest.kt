package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.analyzer.kotlin.KotlinAnalyzerProducer
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.service.wiring.DefaultCapabilities
import io.github.krosstack.kscope.spi.CapabilityRegistry
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.graph.GraphStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class CountingKotlinProducer(private val delegate: KotlinAnalyzerProducer) : FactProducer by delegate {
    var unitsProduced = 0

    override fun produce(request: ProduceRequest, sink: FactSink) {
        unitsProduced += request.units.size
        delegate.produce(request, sink)
    }
}

private class ApiReferenceWorkspace : AutoCloseable {
    val root: Path = Files.createTempDirectory("kscope-api-reference")

    init {
        val fixture = Path.of(checkNotNull(System.getProperty("kscope.fixture")))
        val derived = setOf(".gradle", ".kotlin", ".kscope", "build")
        Files.walk(fixture).use { paths ->
            paths.filter { path -> fixture.relativize(path).none { it.toString() in derived } }.forEach { source ->
                val target = root.resolve(fixture.relativize(source).toString())
                if (Files.isDirectory(source)) Files.createDirectories(target) else Files.copy(source, target)
            }
        }
    }

    override fun close() {
        TemporaryTree.delete(root)
    }
}

class ApiDigestIntegrationTest {
    @Test
    fun `a real body edit produces one Kotlin unit and an API edit produces its dependents`() =
        ApiReferenceWorkspace().use { workspace ->
            val index = workspace.root.resolve(".kscope")
            val registry = DefaultCapabilities.registry(index)
            val kotlin = CountingKotlinProducer(registry.producers.filterIsInstance<KotlinAnalyzerProducer>().single())
            val recording = CapabilityRegistry(
                registry.detectors,
                registry.buildModelProviders,
                registry.synthesizers,
                registry.producers.map { if (it is KotlinAnalyzerProducer) kotlin else it },
                registry.linkers,
            )
            ScanEngine(recording, WorkspaceConfig(workspace.root, index, trusted = false)).use { engine ->
                engine.update()
                kotlin.unitsProduced = 0
                val source = workspace.root.resolve(
                    "core/legacy/src/main/kotlin/sample/tasktide/core/legacy/CsvExporter.kt",
                )
                val original = Files.readString(source)
                val anchor = "        val due = DateUtils.formatIso"
                assertTrue(original.contains(anchor))
                val changed = original.replace(
                    anchor,
                    "        val titleSize = task.title.length\n        val padding = titleSize + 1\n" +
                        "        if (padding < 0) return \"\"\n\n\n" + anchor,
                )
                Files.writeString(source, changed)

                engine.update()

                assertEquals(1, kotlin.unitsProduced)
                assertVerified(engine)
                kotlin.unitsProduced = 0
                Files.writeString(source, changed + "\nfun exportedApiMarker() = 1\n")
                engine.update()
                assertTrue(kotlin.unitsProduced > 1, "Produced ${kotlin.unitsProduced} Kotlin units")
                assertVerified(engine)
            }
        }

    private fun assertVerified(engine: ScanEngine) {
        val store = engine.graph() as GraphStore
        val incremental = store.batches()
        val verification = assertNotNull(engine.update(verify = true).verify)
        if (verification.matches) {
            assertEquals(0, verification.differingUnits)
            return
        }
        val unit = verification.differences.first()
        engine.rebuild()
        val clean = (engine.graph() as GraphStore).batches()
        val previous = incremental.singleOrNull { it.producer == unit.producer && it.owner == unit.owner }
        val rebuilt = clean.singleOrNull { it.producer == unit.producer && it.owner == unit.owner }
        assertTrue(verification.matches, "Unit $unit; first differing fact: ${firstDifference(previous, rebuilt)}")
    }

    private fun firstDifference(incremental: FactBatch?, clean: FactBatch?): String {
        val previous = incremental?.facts.orEmpty()
        val rebuilt = clean?.facts.orEmpty()
        val position = (0 until maxOf(previous.size, rebuilt.size)).firstOrNull {
            previous.getOrNull(it) != rebuilt.getOrNull(it)
        }
        return position?.let { "${previous.getOrNull(it)} != ${rebuilt.getOrNull(it)}" } ?: "fingerprint only"
    }
}
