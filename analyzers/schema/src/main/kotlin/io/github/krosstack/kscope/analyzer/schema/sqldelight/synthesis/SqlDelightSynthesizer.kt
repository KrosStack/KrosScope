package io.github.krosstack.kscope.analyzer.schema.sqldelight.synthesis

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqSource
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightDatabases
import io.github.krosstack.kscope.analyzer.schema.stub.StubSymbols
import io.github.krosstack.kscope.analyzer.schema.stub.StubText
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource

private const val GENERATOR = "sqldelight"

/** The stubs of one module and the facts about the symbols they declare. */
private class ModuleStubs(val sources: List<SyntheticSource>, val facts: List<Fact>)

/**
 * Makes the Kotlin that the SQLDelight plugin generates from the `.sq` files of a module that declares databases, from
 * its naming rules, so that repositories that call queries and read rows bind to them. The database name and package
 * come from the build model. A function whose signature is not fully inferred is marked heuristic.
 */
internal class SqlDelightSynthesizer : SurfaceSynthesizer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.sqldelight-stubs", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.sqldelight),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.MODULE,
    )

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> =
        request.units.flatMap { unit ->
            val stubs = stubsOf(unit, request)
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), stubs.facts))
            stubs.sources
        }

    private fun stubsOf(unit: AnalysisUnit, request: ProduceRequest): ModuleStubs {
        val databases = unit.module?.let(SqlDelightDatabases::of).orEmpty()
        val sources = unit.artifacts.mapNotNull { SqSource.read(it, request.model, request.files) }
        val planned = SqlDelightPlan(sources).stubs().filter { it.placement.sourceSet != null }
        val generated = planned.map { stub ->
            val symbols = StubSymbols(stub.placement.identity, GENERATOR, stub.placement.scope).of(stub.file)
            val declared = symbols.symbols.map { Fact.OfSymbol(it) }
            val facts = declared + symbols.relationships.map { Fact.OfRelationship(it) }
            val source = SyntheticSource(
                virtualPath = stub.virtualPath,
                language = "kotlin",
                text = StubText.render(stub.file),
                sourceSet = checkNotNull(stub.placement.sourceSet).id,
                generator = GENERATOR,
            )
            source to facts
        }
        val notes = listOfNotNull(databaseNote(unit, databases.isEmpty() && sources.isNotEmpty()))
        return ModuleStubs(generated.map { it.first }, generated.flatMap { it.second } + notes)
    }

    private fun databaseNote(unit: AnalysisUnit, isMissing: Boolean): Fact? = unit.module?.takeIf { isMissing }?.let {
        Fact.OfDiagnostic(
            Diagnostic(
                DiagnosticSeverity.INFO,
                ReasonCode("sqldelight.database-unknown"),
                "${it.path}: the build model names no SQLDelight database, so the database interface is not made.",
                null,
            ),
        )
    }
}
