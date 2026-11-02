package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory

internal class GradlePropertiesDetector :
    NameBasedDetector(CoreArtifactTypes.GRADLE_PROPERTIES, ArtifactCategory.BUILD) {
    override fun matches(lowerCaseFileName: String) = lowerCaseFileName == "gradle.properties"
}
