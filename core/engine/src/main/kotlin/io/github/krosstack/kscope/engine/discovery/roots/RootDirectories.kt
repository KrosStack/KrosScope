package io.github.krosstack.kscope.engine.discovery.roots

import io.github.krosstack.kscope.engine.discovery.ArtifactFactory
import io.github.krosstack.kscope.engine.discovery.ContentResult
import io.github.krosstack.kscope.engine.discovery.ContentScanner
import io.github.krosstack.kscope.engine.discovery.WorkspaceWalker
import io.github.krosstack.kscope.engine.scope.ScopeFilter
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Repository
import java.nio.file.Files
import java.nio.file.Path

/** The files that were [candidates] below some directories, and the [artifacts] and [notices] made of those found. */
internal class RootFiles(val candidates: List<String>, val artifacts: List<Artifact>, val notices: List<Diagnostic>)

/**
 * Reads the files below directories from disk, for directories that Git ignores and discovery therefore does not list.
 * A file that is already known is not read again, and a file that the scope excludes is not read at all.
 */
internal class RootDirectories(
    private val root: Path,
    private val artifactFactory: ArtifactFactory,
    private val scope: ScopeFilter,
) {
    fun read(repository: Repository, directories: List<String>, known: Set<String>): RootFiles {
        val candidates = directories.flatMap { filesBelow(it) }.distinct()
        val found = candidates.filter { it !in known && scope.admits(it) { false } }.sorted()
        val outcomes = found.associateWith { ContentScanner.scan(root.resolve(it)) }
        val added = outcomes.mapNotNull { (relative, result) ->
            (result as? ContentResult.Read)?.let { artifactFactory.create(repository, relative, it.content) }
        }
        val unreadable = outcomes.mapNotNull { (relative, result) ->
            (result as? ContentResult.Unreadable)?.let { artifactFactory.unreadableNotice(relative, it.reason) }
        }
        return RootFiles(candidates, added, unreadable)
    }

    private fun filesBelow(directory: String): List<String> {
        val start = root.resolve(directory)
        if (!Files.isDirectory(start)) return emptyList()
        return WorkspaceWalker.relativeFiles(root, start).filter { BACKSLASH !in it }
    }

    private companion object {
        const val BACKSLASH = '\\'
    }
}
