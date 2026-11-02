package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/** Claims C and C++ sources and headers. */
internal class NativeSourceDetector : ArtifactDetector {
    override val id: String = NativeArtifactTypes.source.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val extension = candidate.path.value.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return NativeArtifactTypes.source.takeIf { extension in EXTENSIONS }
    }

    private companion object {
        val EXTENSIONS = setOf("c", "cc", "cpp", "cxx", "c++", "h", "hh", "hpp", "hxx", "h++")
    }
}
