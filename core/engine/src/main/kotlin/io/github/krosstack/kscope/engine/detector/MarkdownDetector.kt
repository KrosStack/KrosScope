package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory

internal class MarkdownDetector : NameBasedDetector(CoreArtifactTypes.MARKDOWN, ArtifactCategory.DOCUMENTATION) {
    override fun matches(lowerCaseFileName: String) =
        lowerCaseFileName.endsWith(".md") || lowerCaseFileName.endsWith(".markdown")
}
