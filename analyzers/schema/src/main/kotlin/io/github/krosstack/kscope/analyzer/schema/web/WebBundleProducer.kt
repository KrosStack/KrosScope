package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.web.facts.UnitFacts
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
 * Turns the web files of each repository into the symbols of its npm packages, with the directory that their bundlers
 * write to, and of its scripts, and links the files of a bundle that a build left on disk to the scripts they were
 * built from, by their source maps. A script is any hand-written TypeScript or JavaScript file that a module contains,
 * in one of its source sets or beside them, such as the `web` folder of a Gradle module. A source map may name a script
 * of another module, so the unit is the repository, which runs again when any web file of it changes. Discovery reads
 * the bundle only once a build has written it, so the unit runs again when the build changes the bundle. Every
 * repository answers with one batch.
 */
internal class WebBundleProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.web.bundles", "0.1.0"),
        consumes = WebArtifactTypes.all,
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.REPOSITORY,
    )

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), UnitFacts(unit, request).all()))
        }
    }
}
