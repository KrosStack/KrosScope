package io.github.krosstack.kscope.gradle.model.publication

import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask

/**
 * The file names of the archives a project builds: that of its `jar` task and, in a multiplatform project, `jvmJar`.
 * An Android library that is not multiplatform names the AAR it publishes, `<archive name>-<version>.aar`, or
 * `<archive name>.aar` when it sets no version; the archive name is that of the base extension, otherwise the name of
 * the project.
 */
internal object ArchivesSection {
    private val taskNames = listOf("jar", "jvmJar")
    private const val ANDROID_LIBRARY = "com.android.library"
    private const val MULTIPLATFORM = "org.jetbrains.kotlin.multiplatform"
    private const val UNSPECIFIED = "unspecified"

    fun describe(project: Project): List<String> = (jars(project) + listOfNotNull(aarOf(project))).sorted()

    private fun jars(project: Project): List<String> = taskNames
        .filter { it in project.tasks.names }
        .mapNotNull { project.tasks.getByName(it) as? AbstractArchiveTask }
        .map { it.archiveFileName.get() }

    private fun aarOf(project: Project): String? {
        val plugins = project.pluginManager
        if (!plugins.hasPlugin(ANDROID_LIBRARY) || plugins.hasPlugin(MULTIPLATFORM)) return null
        val name = project.extensions.findByType(BasePluginExtension::class.java)?.archivesName?.orNull
            ?: project.name
        val version = project.version.toString().takeIf { it.isNotBlank() && it != UNSPECIFIED }
        return if (version == null) "$name.aar" else "$name-$version.aar"
    }
}
