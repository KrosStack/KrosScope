package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory

/** Kotlin scripts other than Gradle settings and build scripts. */
internal class KotlinScriptDetector : NameBasedDetector(CoreArtifactTypes.KOTLIN_SCRIPT, ArtifactCategory.SOURCE) {
    override fun matches(lowerCaseFileName: String) = lowerCaseFileName.endsWith(".kts") &&
        !GradleSettingsDetector.isSettingsName(lowerCaseFileName) &&
        !GradleBuildScriptDetector.isBuildScriptName(lowerCaseFileName)
}
