package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument
import io.github.krosstack.kscope.model.SourceSetId

/**
 * Gives each source set the classpath entries of every compilation or variant it is compiled in. A shared source set
 * such as `commonMain` therefore gets the union over its platforms, and `main` of an Android module the union over
 * its variants. Entries keep the order of the Gradle model and appear once.
 */
internal object ClasspathMapper {
    fun map(document: ProjectDocument, sourceSets: Map<String, SourceSetId>): Map<SourceSetId, List<String>> {
        val collected = linkedMapOf<SourceSetId, LinkedHashSet<String>>()
        document.classpaths.entries.forEach { entry ->
            val files = entry.files.mapNotNull { document.classpaths.files.getOrNull(it) }
            entry.sourceSets.mapNotNull(sourceSets::get).forEach { id ->
                collected.getOrPut(id) { LinkedHashSet() }.addAll(files)
            }
        }
        return collected.filterValues { it.isNotEmpty() }
            .mapValues { it.value.toList() }
            .toSortedMap(compareBy { it.value })
    }
}
