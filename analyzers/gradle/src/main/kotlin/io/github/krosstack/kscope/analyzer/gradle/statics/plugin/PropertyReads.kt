package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import java.util.concurrent.ConcurrentHashMap

private const val DIRECT = """(?:project\.)?(?:findProperty|property)\(\s*(["'])([\w.-]+)\1\s*\)"""
private const val PROVIDED = """(?:project\.)?providers\.gradleProperty\(\s*(["'])([\w.-]+)\3\s*\)\.get\(\)"""
private const val CONVERTED = """(?:\.toString\(\)|\s+as\s+String)?"""
private val propertyRead = Regex("(?:$DIRECT|$PROVIDED)$CONVERTED")
private val projectOwnNames = setOf(
    "name", "path", "displayName", "buildTreePath", "identityPath", "depth", "state",
    "project", "rootProject", "parent", "childProjects", "allprojects", "subprojects",
    "projectDir", "rootDir", "buildFile", "layout", "gradle", "properties",
)

/**
 * Resolves the Gradle property that an expression reads by a literal key: `findProperty("k")`, `property("k")` or
 * `providers.gradleProperty("k").get()`, each with an optional `project.` and a trailing `.toString()` or `as String`.
 * A provider read sees the Gradle properties of the build, so it takes the value from the `gradle.properties` of the
 * build root. A direct read sees the properties of the project, so it takes the value from the `gradle.properties` of
 * the module directory or, failing that, of the build root; it is left unread when the scripts of the module assign
 * the same name, because the value is then the one the script gives, which is not resolved, and when the name is one
 * of the project's own read-only properties, such as `name` or `path`, which Gradle returns before the file value. The
 * `gradle.properties` of the user home and the environment are never read, so that the result does not depend on the
 * machine.
 */
internal class PropertyReads(private val disk: WorkspaceDisk, private val rootDirectory: String) {
    private val files = ConcurrentHashMap<String, Map<String, String>>()

    fun valueOf(expression: String, directory: String, sources: List<ScriptSource>): String? {
        val match = propertyRead.matchEntire(expression.trim()) ?: return null
        val provided = match.groupValues[4]
        val key = provided.ifEmpty { match.groupValues[2] }
        val directories = when {
            provided.isNotEmpty() -> listOf(rootDirectory)
            key in projectOwnNames || PropertyAssignments.assign(key, sources) -> emptyList()
            else -> listOf(directory, rootDirectory)
        }
        return lookup(key, directories)
    }

    private fun lookup(key: String, directories: List<String>): String? =
        directories.firstNotNullOfOrNull { propertiesOf(it)[key] }?.takeIf { it.isNotBlank() }

    private fun propertiesOf(directory: String): Map<String, String> =
        files.computeIfAbsent(directory) { GradleProperties.read(disk, it) }
}
