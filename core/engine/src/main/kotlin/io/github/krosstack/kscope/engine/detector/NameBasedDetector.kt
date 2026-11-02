package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/** Detects by file name alone; names are compared in lower case because file systems differ in case sensitivity. */
internal abstract class NameBasedDetector(final override val id: String, category: ArtifactCategory) :
    ArtifactDetector {
    private val type = ArtifactType(id, category)

    protected abstract fun matches(lowerCaseFileName: String): Boolean

    final override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val fileName = candidate.path.value.substringAfterLast('/').lowercase()
        return type.takeIf { matches(fileName) }
    }
}
