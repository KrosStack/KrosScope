package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.detector.GradleSettingsDetector

/** The ecosystem that a project marker file names. */
internal enum class Ecosystem(val label: String) {
    NODE("node"),
    CARGO("cargo"),
    PYTHON("python"),
    GO("go"),
    MAVEN("maven"),
    SWIFT("swift"),
}

/**
 * Recognises the files that mark the root of a build or project. This is the only place where the structure report
 * looks at file names, as artifact detectors are for the index; names are compared in lower case.
 */
internal object ProjectMarkerDetector {
    private val projectMarkers = mapOf(
        "package.json" to Ecosystem.NODE,
        "cargo.toml" to Ecosystem.CARGO,
        "pyproject.toml" to Ecosystem.PYTHON,
        "go.mod" to Ecosystem.GO,
        "pom.xml" to Ecosystem.MAVEN,
        "package.swift" to Ecosystem.SWIFT,
    )

    fun isGradleSettings(fileName: String): Boolean = GradleSettingsDetector.isSettingsName(fileName.lowercase())

    fun otherEcosystem(fileName: String): Ecosystem? = projectMarkers[fileName.lowercase()]
}
