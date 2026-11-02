package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory

internal class GradleSettingsDetector : NameBasedDetector(CoreArtifactTypes.GRADLE_SETTINGS, ArtifactCategory.BUILD) {
    override fun matches(lowerCaseFileName: String) = isSettingsName(lowerCaseFileName)

    companion object {
        private val names = setOf("settings.gradle", "settings.gradle.kts")

        fun isSettingsName(lowerCaseFileName: String) = lowerCaseFileName in names
    }
}
