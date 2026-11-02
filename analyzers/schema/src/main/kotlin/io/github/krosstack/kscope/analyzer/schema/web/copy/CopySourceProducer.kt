package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor

/**
 * Makes the symbols of the paths that the Gradle tasks of a workspace copy from: the `processResources` of a module,
 * its `Copy` and `Sync` tasks and its `copy` and `sync` calls, in the build scripts of any repository. A path may lie
 * in another repository than the script, so the one unit is the workspace, which depends on every build script and
 * on the build model. It always answers with one batch.
 */
internal class CopySourceProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.web.copy-sources", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.buildScript),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override fun produce(request: ProduceRequest, sink: FactSink) {
        val unit = request.units.firstOrNull() ?: return
        sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), CopySources(request).facts()))
    }
}
