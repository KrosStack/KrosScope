package io.github.krosstack.kscope.analyzer.gradle

/** The ids of the artifact types this analyzer consumes; the engine detects them. */
internal object ArtifactTypes {
    const val GRADLE_SETTINGS = "gradle.settings"
    const val GRADLE_BUILD_SCRIPT = "gradle.build-script"
    const val GRADLE_VERSION_CATALOG = "gradle.version-catalog"
    const val GRADLE_PROPERTIES = "gradle.properties"
    const val KOTLIN_SCRIPT = "kotlin.script"
    const val KOTLIN_SOURCE = "kotlin.source"
}
