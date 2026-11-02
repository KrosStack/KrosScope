package io.github.krosstack.kscope.analyzer.jni.loads

import io.github.krosstack.kscope.analyzer.jni.JniNotes
import io.github.krosstack.kscope.analyzer.jni.SymbolAnchor
import io.github.krosstack.kscope.model.Artifact
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
 * Turns the Kotlin and Java files of each source set into the symbols of the calls that load a native library, found
 * by the lexical match of [LoadCallScanner]. The Kotlin analyzer reads the same files and knows nothing of these
 * calls: its units and this producer's are separate. A source set without such a call answers with no fact. A file
 * that cannot be read leaves a note, and so does a file with a call while no module contains it; the other files of
 * the source set still produce their symbols.
 */
internal class LoadCallProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.jni.load-calls", "0.2.0"),
        consumes = JvmSourceTypes.all,
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
    } catch (failure: IOException) {
        listOf(JniNotes.unreadable(artifact, failure))
    }

    private fun factsOf(unit: AnalysisUnit, artifact: Artifact, text: String): List<Fact> {
        val dialect = if (artifact.type == JvmSourceTypes.java) JvmDialect.JAVA else JvmDialect.KOTLIN
        val calls = LoadCallScanner(text, dialect).scan().distinctBy { it.library }
        val module = unit.module
        return when {
            calls.isEmpty() -> emptyList()

            module == null -> listOf(JniNotes.moduleUnknown(artifact))

            else -> {
                val facts = LoadCallFacts(SymbolAnchor(artifact, module, unit.sourceSet))
                calls.map { Fact.OfSymbol(facts.symbolOf(it)) }
            }
        }
    }
}
