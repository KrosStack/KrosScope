package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/** Claims Protocol Buffers definition files. */
internal class ProtoSourceDetector : ArtifactDetector {
    override val id: String = SchemaArtifactTypes.proto.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? =
        SchemaArtifactTypes.proto.takeIf { candidate.path.value.endsWith(".proto") }
}
