package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution

/**
 * The JAR files that a dependency on `file:<path>` names: the file itself, or each JAR below the directory that the
 * path ends with, as it is on disk now, at any depth and keeping its path from the directory, when the dependency's
 * include and exclude patterns select it. A missing directory names nothing.
 */
internal class ArchiveDeclarations(private val roots: CheckoutRoots) {
    fun of(edge: Relationship, consumer: Module, repository: RepositoryId?): List<DeclaredArchive> {
        val path = pathOf(edge)
        val paths = when {
            path.endsWith(JAR) -> listOf(path)
            path.endsWith(DIRECTORY) -> treeOf(edge, path, repository)
            else -> emptyList()
        }
        return paths.map { DeclaredArchive(edge, consumer, repository, it) }
    }

    private fun pathOf(edge: Relationship): String {
        val target = (edge.resolution as? Resolution.Resolved)?.target as? NodeRef.OfExternal
        val coordinate = target?.coordinate?.takeIf { edge.kind == RelationKind.DEPENDS_ON }
        return coordinate?.takeIf { it.startsWith(FILE) }?.removePrefix(FILE).orEmpty()
    }

    private fun treeOf(edge: Relationship, directory: String, repository: RepositoryId?): List<String> {
        val location = roots.resolve(repository, directory) ?: return emptyList()
        return ArchiveTree.jarsIn(location, TreeFilter.of(edge.attributes)).map { directory + it }
    }

    private companion object {
        const val FILE = "file:"
        const val JAR = ".jar"
        const val DIRECTORY = "/"
    }
}
