package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import java.io.StringReader
import java.util.Properties

private const val FILE = "gradle.properties"

/** The properties of the `gradle.properties` file of a build root, read by the rules of `java.util.Properties`. */
internal object GradleProperties {
    fun read(disk: WorkspaceDisk, directory: String): Map<String, String> =
        disk.readText(RepoPaths.join(directory, FILE))?.let(::parse).orEmpty()

    fun parse(text: String): Map<String, String> {
        val properties = Properties()
        try {
            properties.load(StringReader(text))
        } catch (ignored: IllegalArgumentException) {
            return emptyMap()
        }
        return properties.stringPropertyNames().sorted().associateWith { properties.getProperty(it) }
    }
}
