package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlParseResult
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlParser
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

/**
 * Turns each `.aidl` file into the symbols of the contracts it declares: interfaces, their methods and constants, and
 * parcelables. A file that cannot be read gives one warning with the position of the problem.
 */
internal class AidlProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.aidl", "0.1.0"),
        consumes = setOf(AidlArtifactTypes.source),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val facts = factsOf(unit, artifact, request.files)
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(artifact.contentHash.value), facts))
        }
    }

    private fun factsOf(unit: AnalysisUnit, artifact: Artifact, files: WorkspaceFiles): List<Fact> {
        val module = unit.module
        val sourceSet = unit.sourceSet
        return if (module == null || sourceSet == null) {
            listOf(AidlNotes.of(artifact, DiagnosticSeverity.INFO, "module-unknown", "no source set contains it"))
        } else {
            readFacts(artifact, AidlFacts(artifact, module, sourceSet), files)
        }
    }

    private fun readFacts(artifact: Artifact, facts: AidlFacts, files: WorkspaceFiles): List<Fact> = try {
        parsedFacts(artifact, facts, files.read(artifact.ref).toString(Charsets.UTF_8))
    } catch (failure: IOException) {
        listOf(AidlNotes.of(artifact, DiagnosticSeverity.WARNING, "unreadable", failure.message.orEmpty()))
    }

    private fun parsedFacts(artifact: Artifact, facts: AidlFacts, text: String): List<Fact> =
        when (val parsed = AidlParser.parse(text)) {
            is AidlParseResult.Malformed -> listOf(AidlNotes.parseError(artifact, parsed.problem))
            is AidlParseResult.Parsed -> facts.of(parsed.file) + AidlNotes.skipped(artifact, parsed.file)
        }
}
