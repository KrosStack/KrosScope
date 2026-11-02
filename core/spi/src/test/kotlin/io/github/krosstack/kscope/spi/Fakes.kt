package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.graph.GraphReader

internal fun descriptorOf(id: String) =
    ProducerDescriptor(ProducerRef(id, "1.0.0"), emptySet(), emptySet(), OwnershipKind.ARTIFACT)

internal class FakeDetector(override val id: String) : ArtifactDetector {
    override fun detect(candidate: ArtifactCandidate): ArtifactType? = null
}

internal class FakeBuildModelProvider(id: String) : BuildModelProvider {
    override val producer = ProducerRef(id, "1.0.0")
    override val requiresTrust = false

    override fun provide(request: BuildModelRequest): BuildModelResult = BuildModelResult.NotApplicable
}

internal class FakeSynthesizer(id: String) : SurfaceSynthesizer {
    override val descriptor = descriptorOf(id)

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> = emptyList()
}

internal class FakeProducer(id: String) : FactProducer {
    override val descriptor = descriptorOf(id)

    override fun produce(request: ProduceRequest, sink: FactSink) = Unit
}

internal class FakeLinker(id: String, override val namespaces: Set<String> = emptySet()) : BoundaryLinker {
    override val descriptor = descriptorOf(id)

    override fun link(graph: GraphReader, sink: FactSink) = Unit
}
