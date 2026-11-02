package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.PublicationDocument

private const val UNSPECIFIED = "unspecified"
private const val PARTS = 3

/** The module attributes that record what a module publishes, shared by the static and the Tooling API provider. */
internal object PublicationAttributes {
    const val PUBLICATIONS = "maven.publications"
    const val HEURISTIC = "maven.heuristic"

    fun of(coordinates: Collection<String>, heuristic: Boolean): Map<String, String> {
        val sorted = coordinates.distinct().sorted()
        if (sorted.isEmpty()) return emptyMap()
        val publications = PUBLICATIONS to sorted.joinToString(",")
        return if (heuristic) mapOf(publications, HEURISTIC to "true") else mapOf(publications)
    }

    /** One entry per Maven publication of the project; a project that publishes nothing records nothing. */
    fun of(document: PublicationDocument?): Map<String, String> {
        val publications = document?.publications.orEmpty()
        return of(publications.mapNotNull { coordinateOf(it.groupId, it.artifactId, it.version) }, heuristic = false)
    }

    private fun coordinateOf(group: String?, artifact: String?, version: String?): String? {
        val parts = listOfNotNull(group, artifact, version).filter { it.isNotBlank() }
        return parts.joinToString(":").takeIf { parts.size == PARTS && version != UNSPECIFIED }
    }
}
