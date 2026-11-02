package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import java.io.IOException

/**
 * A producer whose unit is one file: it reads the text of the file and answers with the facts that [factsOf] makes of
 * it, or with a note when the file could not be read. The fingerprint of the batch is the content hash of the file.
 */
internal abstract class SingleArtifactProducer : FactProducer {
    protected abstract fun factsOf(unit: AnalysisUnit, artifact: Artifact, text: String): List<Fact>

    final override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val facts = try {
                factsOf(unit, artifact, request.files.read(artifact.ref).toString(Charsets.UTF_8))
            } catch (failure: IOException) {
                listOf(JniNotes.unreadable(artifact, failure))
            }
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(artifact.contentHash.value), facts))
        }
    }
}
