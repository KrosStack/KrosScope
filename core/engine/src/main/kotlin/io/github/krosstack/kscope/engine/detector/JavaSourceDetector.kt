package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory

internal class JavaSourceDetector : NameBasedDetector(CoreArtifactTypes.JAVA_SOURCE, ArtifactCategory.SOURCE) {
    override fun matches(lowerCaseFileName: String) = lowerCaseFileName.endsWith(".java")
}
