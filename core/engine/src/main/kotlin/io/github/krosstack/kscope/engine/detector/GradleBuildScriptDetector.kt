package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory

internal class GradleBuildScriptDetector :
    NameBasedDetector(CoreArtifactTypes.GRADLE_BUILD_SCRIPT, ArtifactCategory.BUILD) {
    override fun matches(lowerCaseFileName: String) = isBuildScriptName(lowerCaseFileName)

    companion object {
        private val names = setOf("build.gradle", "build.gradle.kts")

        fun isBuildScriptName(lowerCaseFileName: String) = lowerCaseFileName in names
    }
}
