package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project

/** The build a project belongs to; the root project of a build also lists the builds it includes. */
internal object BuildInfo {
    fun describe(project: Project): Map<String, Any?> {
        val gradle = project.gradle
        val root = gradle.rootProject
        val info = linkedMapOf<String, Any?>(
            "name" to root.name,
            "rootDir" to Values.slashPath(root.projectDir),
            "included" to (gradle.parent != null),
            "gradleVersion" to gradle.gradleVersion,
        )
        if (project == root) info["includedBuilds"] = includedBuilds(project)
        return info
    }

    private fun includedBuilds(project: Project): List<Map<String, String>> = project.gradle.includedBuilds
        .map { linkedMapOf("name" to it.name, "dir" to Values.slashPath(it.projectDir)) }
        .sortedBy { it.getValue("name") }
}
