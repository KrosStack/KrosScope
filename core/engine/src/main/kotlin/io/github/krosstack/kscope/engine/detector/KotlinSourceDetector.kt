package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory

internal class KotlinSourceDetector : NameBasedDetector(CoreArtifactTypes.KOTLIN_SOURCE, ArtifactCategory.SOURCE) {
    override fun matches(lowerCaseFileName: String) = lowerCaseFileName.endsWith(".kt")
}
