package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/** Claims the build files of native code that have one fixed name, such as `CMakeLists.txt` or `Cargo.toml`. */
internal class NativeBuildFileDetector(private val type: ArtifactType, private val fileName: String) :
    ArtifactDetector {
    override val id: String = type.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? =
        type.takeIf { candidate.path.value.substringAfterLast('/').equals(fileName, ignoreCase = true) }
}
