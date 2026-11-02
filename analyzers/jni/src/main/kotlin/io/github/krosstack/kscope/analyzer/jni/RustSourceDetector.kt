package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/** Claims Rust sources. */
internal class RustSourceDetector : ArtifactDetector {
    override val id: String = NativeArtifactTypes.rustSource.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val extension = candidate.path.value.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return NativeArtifactTypes.rustSource.takeIf { extension == "rs" }
    }
}
