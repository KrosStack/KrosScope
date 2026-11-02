package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory

internal class GradleVersionCatalogDetector :
    NameBasedDetector(CoreArtifactTypes.GRADLE_VERSION_CATALOG, ArtifactCategory.BUILD) {
    override fun matches(lowerCaseFileName: String) = lowerCaseFileName.endsWith(".versions.toml")
}
