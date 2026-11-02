package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.mapping.PublicationAttributes
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptDiagnostics
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.model.Diagnostic

private const val MAX_EXPRESSION = 80
private const val ROOT_PATH = ":"
private const val UNSPECIFIED = "unspecified"
private const val VERSION = "version"

/** What a module publishes: the attributes that record it and what could not be read. */
internal class PublicationFacts(val attributes: Map<String, String>, val problems: List<Diagnostic>) {
    companion object {
        val none = PublicationFacts(emptyMap(), emptyList())
    }
}

private class Resolved(
    val coordinate: String?,
    val heuristic: Boolean,
    val unreadable: List<String>,
    val unspecified: List<String>,
)

/**
 * Reads the coordinates a module publishes, from string literals and from Gradle properties read by a literal key: the
 * `group` and `version` of its scripts or, failing that, of the `gradle.properties` of its build root, the artifact id
 * from the Gradle path, and the publications and coordinates the scripts declare. A module without declared
 * publications publishes one entry. A publication whose group and artifact id are read but whose version is computed
 * is recorded with the version `unspecified` and as heuristic, so that it is linked by group and artifact only.
 */
internal class PublicationReader(private val values: ProjectValues, private val rootName: String?) {
    fun read(path: String, directory: String, ids: Set<String>, sources: List<ScriptSource>): PublicationFacts {
        if (ids.none { it in PluginTable.publishingPlugins }) return PublicationFacts.none
        val group = values.of("group", sources, directory)
        val version = values.of(VERSION, sources, directory)
        val artifact = artifactIdOf(path)
        val declared = PublicationDeclarations.read(sources).ifEmpty { listOf(Declared(null, null, null)) }
        val resolved = declared.map { resolve(resolvedIn(it, directory, sources), group, artifact, version) }
        val found = resolved.filter { it.coordinate != null }
        val attributes = PublicationAttributes.of(found.mapNotNull { it.coordinate }, found.any { it.heuristic })
        val unreadable = resolved.flatMap { it.unreadable }.distinct()
        val unspecified = resolved.flatMap { it.unspecified }.distinct()
        val problems = listOfNotNull(
            ScriptDiagnostics.publicationNotAnalysed(path, unreadable).takeIf { unreadable.isNotEmpty() },
            ScriptDiagnostics.publicationVersionNotRead(path, unspecified).takeIf { unspecified.isNotEmpty() },
        )
        return PublicationFacts(attributes, problems)
    }

    private fun resolvedIn(declared: Declared, directory: String, sources: List<ScriptSource>) = Declared(
        declared.groupId?.let { values.resolved(it, directory, sources) },
        declared.artifactId?.let { values.resolved(it, directory, sources) },
        declared.version?.let { values.resolved(it, directory, sources) },
    )

    private fun resolve(declared: Declared, group: Part?, artifact: Part?, version: Part?): Resolved {
        val parts = listOf(
            "group" to (declared.groupId ?: group),
            "artifact id" to (declared.artifactId ?: artifact),
            VERSION to (declared.version ?: version),
        )
        val unreadable = parts.filter { it.second?.text == null }
        val reasons = unreadable.map { (name, part) -> reasonOf(name, part) }
        if (unreadable.any { it.first != VERSION }) return Resolved(null, false, reasons, emptyList())
        val heuristic = unreadable.isNotEmpty() || parts.any { it.second?.heuristic == true }
        return Resolved(parts.joinToString(":") { it.second?.text ?: UNSPECIFIED }, heuristic, emptyList(), reasons)
    }

    private fun reasonOf(name: String, part: Part?): String {
        val expression = part?.expression?.take(MAX_EXPRESSION)
        return if (expression == null) "the $name is not set" else "the $name is computed ('$expression')"
    }

    private fun artifactIdOf(path: String): Part? {
        val name = if (path == ROOT_PATH) rootName else path.substringAfterLast(':')
        return name?.takeIf { it.isNotEmpty() }?.let { Part(it, it, false) }
    }
}
