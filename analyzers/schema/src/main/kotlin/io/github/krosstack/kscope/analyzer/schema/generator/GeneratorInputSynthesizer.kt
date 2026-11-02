package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource

/**
 * Links the Gradle modules of a workspace to the spec files that their code generators read, in whichever repository
 * the files are, and makes the Kotlin classes that the generators make of them. The OpenAPI Generator reads an
 * `inputSpec` and Wire reads the `sourcePath` of its block; the paths are those a build script writes as literals. The
 * one unit is the workspace, because a script of one repository reads files of another: it depends on every build
 * script and every spec file, so a change to any of them makes the generated classes again. It always answers with one
 * batch, empty when no script gives an input.
 */
internal class GeneratorInputSynthesizer : SurfaceSynthesizer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.generator-inputs", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.buildScript, SchemaArtifactTypes.proto, SchemaArtifactTypes.openApi),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> {
        val unit = request.units.firstOrNull() ?: return emptyList()
        val outcome = GeneratorInputs(request).outcome()
        sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), outcome.facts))
        return outcome.sources
    }
}
