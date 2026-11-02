package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

internal class AndroidManifestDetector : ArtifactDetector {
    override val id: String = AndroidArtifactTypes.manifest.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? =
        AndroidArtifactTypes.manifest.takeIf { candidate.path.value.substringAfterLast('/') == MANIFEST_FILE_NAME }

    private companion object {
        const val MANIFEST_FILE_NAME = "AndroidManifest.xml"
    }
}
