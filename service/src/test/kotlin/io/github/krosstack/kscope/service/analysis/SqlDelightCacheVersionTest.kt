package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.analyzer.schema.SchemaCapabilities
import io.github.krosstack.kscope.engine.CoreCapabilities
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.CapabilityRegistry
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlDelightCacheVersionTest {
    private val current = SchemaCapabilities.synthesizers().single {
        it.descriptor.producer.id == "kscope.schema.sqldelight-stubs"
    }
    private val previous = current.descriptor.producer.copy(version = "0.1.0")
    private val obsoleteSource = "obsolete SQLDelight stub"

    @Test
    fun `an unchanged workspace recomputes SQLDelight stubs cached under the previous producer version`() {
        SampleWorkspace("sqld").use { sample ->
            val config = WorkspaceConfig(sample.root)
            val cache = config.indexDirectory.resolve("synthetic-sources.json")
            ScanEngine(registry(previousSynthesizer()), config).use { engine ->
                engine.update()
                assertTrue(Files.readString(cache).contains(obsoleteSource))
                assertEquals(0, engine.update().unitsProduced)
                assertTrue(Files.readString(cache).contains(obsoleteSource))
            }
            ScanEngine(registry(current), config).use { engine ->
                val report = engine.update()
                assertEquals(1, report.unitsProduced, "The SQLDelight synthesis unit must be recomputed")
                assertEquals(1, report.unitsRemoved, "The previous producer's ledger entry must be removed")
                val cached = Files.readString(cache)
                assertFalse(cached.contains(obsoleteSource))
                assertEquals(
                    setOf("${current.descriptor.producer.id}@${current.descriptor.producer.version}"),
                    KscopeJson.parseToJsonElement(cached).jsonObject.keys,
                )
                assertEquals(0, engine.update().unitsProduced)
                assertTrue(engine.update(verify = true).verify?.matches == true)
            }
        }
    }

    private fun previousSynthesizer() = object : SurfaceSynthesizer {
        override val descriptor = current.descriptor.copy(producer = previous)

        override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> =
            current.synthesize(request) { sink.accept(it.copy(producer = previous)) }
                .map { it.copy(text = obsoleteSource) }
    }

    private fun registry(synthesizer: SurfaceSynthesizer) = CapabilityRegistry(
        detectors = CoreCapabilities.detectors() + SchemaCapabilities.detectors(),
        buildModelProviders = CoreCapabilities.buildModelProviders(),
        synthesizers = listOf(synthesizer),
    )
}
