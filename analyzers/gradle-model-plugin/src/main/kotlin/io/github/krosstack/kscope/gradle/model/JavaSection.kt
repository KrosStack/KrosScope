package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

/** The source sets of the Java plugins (`java`, `java-library`), which Kotlin/JVM builds on. */
internal object JavaSection {
    fun describe(project: Project): List<Map<String, Any?>>? {
        val extension = project.extensions.findByType(JavaPluginExtension::class.java) ?: return null
        return extension.sourceSets.map { sourceSet ->
            linkedMapOf(
                "name" to sourceSet.name,
                "javaDirs" to SourceDirs.existing(sourceSet.java.srcDirs),
                "resourceDirs" to SourceDirs.existing(sourceSet.resources.srcDirs),
            )
        }.sortedBy { it["name"].toString() }
    }
}
