package io.github.krosstack.kscope.analyzer.schema.ktor

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.SymbolSite
import io.github.krosstack.kscope.analyzer.schema.TextIndex
import io.github.krosstack.kscope.analyzer.schema.fileNote
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

private const val NOTE_PREFIX = "ktor"

/**
 * Turns the Kotlin files of each source set into the symbols of the routes of the Ktor routing DSL that they declare,
 * found by the lexical match of [KtorRouteScanner]. The Kotlin analyzer reads the same files and knows nothing of these
 * routes: its units and this producer's are separate. Every source set answers with a batch, empty when it declares no
 * route. A file that cannot be read leaves a note, and so does a file with routes while no module contains it; the
 * other files of the source set still produce their symbols.
 */
internal class KtorRouteProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.ktor-routes", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.kotlinSource),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.SOURCE_SET,
    )

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val facts = unit.artifacts.flatMap { factsOfFile(unit, it, request.files) }
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), facts))
        }
    }

    private fun factsOfFile(unit: AnalysisUnit, artifact: Artifact, files: WorkspaceFiles): List<Fact> = try {
        factsOf(unit, artifact, files.read(artifact.ref).toString(Charsets.UTF_8))
    } catch (_: IOException) {
        listOf(note(artifact, DiagnosticSeverity.INFO, "unreadable", "it could not be read"))
    }

    private fun factsOf(unit: AnalysisUnit, artifact: Artifact, text: String): List<Fact> {
        val routes = KtorRouteScanner(text).scan()
        val module = unit.module
        return when {
            routes.isEmpty() -> emptyList()

            module == null ->
                listOf(note(artifact, DiagnosticSeverity.INFO, "module-unknown", "no module contains it"))

            else -> {
                val placement = Placement(module, unit.sourceSet)
                val site = SymbolSite(artifact.ref, placement.scope, TextIndex(text))
                val symbols = KtorRouteSymbols(placement.identity, artifact.ref.path.value).of(routes)
                symbols.map { Fact.OfSymbol(it.toSymbol(site)) }
            }
        }
    }

    private fun note(artifact: Artifact, severity: DiagnosticSeverity, code: String, message: String) =
        fileNote(artifact, severity, "$NOTE_PREFIX.$code", message)
}
