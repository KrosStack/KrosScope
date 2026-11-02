package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.fileNote
import io.github.krosstack.kscope.analyzer.schema.stub.StubSymbols
import io.github.krosstack.kscope.analyzer.schema.stub.StubText
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource

private const val WIRE_KOTLIN_ATTRIBUTE = "wire.kotlin"
private const val GENERATOR = "wire"

/** A stub made for one proto file, with the facts about the symbols it declares and about the file. */
private class Stubbed(val source: SyntheticSource?, val facts: List<Fact>)

/**
 * Makes the Kotlin classes that the Wire plugin generates from the proto files of a module that has it generate Kotlin,
 * from the naming rules of the generator, so that Kotlin code that builds, reads and matches on the messages and enums
 * binds to them. Every proto file of the module gets a stub in its own source set.
 */
internal class WireKotlinSynthesizer : SurfaceSynthesizer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.wire-kotlin", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.proto),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.MODULE,
    )

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> {
        val readings = ProtoReadings(request)
        val maker = StubMaker(request, readings, ProtoTypeIndex(readings.indexed()))
        val stubbed = request.units.map { unit -> unit to stubsOf(unit, maker) }
        stubbed.forEach { (unit, stubs) ->
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), stubs.flatMap { it.facts }))
        }
        return stubbed.flatMap { (_, stubs) -> stubs.mapNotNull { it.source } }
    }

    private fun stubsOf(unit: AnalysisUnit, maker: StubMaker): List<Stubbed> {
        val generatesKotlin = unit.module?.attributes?.get(WIRE_KOTLIN_ATTRIBUTE) == "true"
        return if (generatesKotlin) unit.artifacts.map(maker::stubOf) else emptyList()
    }
}

private class StubMaker(
    private val request: ProduceRequest,
    private val readings: ProtoReadings,
    private val types: ProtoTypeIndex,
) {
    fun stubOf(artifact: Artifact): Stubbed {
        val placement = Placement.of(artifact, request.model)
        val sourceSet = placement?.sourceSet
        val file = readings.of(artifact)
        return when {
            file == null -> Stubbed(null, emptyList())
            placement == null || sourceSet == null -> Stubbed(null, listOf(noSourceSet(artifact)))
            else -> made(artifact, file, placement, sourceSet)
        }
    }

    private fun made(artifact: Artifact, file: ProtoFile, placement: Placement, sourceSet: SourceSet): Stubbed {
        val stub = WireKotlinPlan(file, ProtoIdentities(placement.identity, file.packageName), types).stub()
        val generated = StubSymbols(placement.identity, GENERATOR, placement.scope).of(stub)
        val source = SyntheticSource(
            virtualPath = "wire/${artifact.ref.path.value.replace('/', '_')}.kt",
            language = "kotlin",
            text = StubText.render(stub),
            sourceSet = sourceSet.id,
            generator = GENERATOR,
        )
        val symbols = generated.symbols.map { Fact.OfSymbol(it) }
        val facts = symbols + generated.relationships.map { Fact.OfRelationship(it) }
        return Stubbed(source, facts)
    }

    private fun noSourceSet(artifact: Artifact) = fileNote(
        artifact,
        DiagnosticSeverity.INFO,
        "wire.no-source-set",
        "it is in no source set, so the Kotlin classes of its messages have nowhere to be declared",
    )
}
