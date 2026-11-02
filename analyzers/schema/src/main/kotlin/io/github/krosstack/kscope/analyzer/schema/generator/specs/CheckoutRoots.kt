package io.github.krosstack.kscope.analyzer.schema.generator.specs

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path

/** A place in the checkout of [repository]: a normalized path below its directory, empty for the directory itself. */
internal data class CheckoutPath(val repository: RepositoryId, val path: String)

/**
 * The directories that the repositories of a workspace are checked out in. The files interface gives the location of
 * a file only, so the directory of a repository is found by taking the path of one of its artifacts off the end of
 * the location of that artifact. These locations are used to compare places on disk and never leave this analysis.
 * A location is in the checkout that holds it, the innermost one when checkouts nest.
 */
internal class CheckoutRoots(files: WorkspaceFiles, artifacts: List<Artifact>) {
    private val roots: Map<RepositoryId, Path> = artifacts.groupBy { it.ref.repository }.mapValues { (_, members) ->
        val ref = members.first().ref
        rootOf(files.absolutePath(ref), ref.path.value.split('/').size)
    }

    fun of(repository: RepositoryId): Path? = roots[repository]

    fun locate(location: Path): CheckoutPath? = roots.entries.filter { location.startsWith(it.value) }
        .maxByOrNull { it.value.nameCount }
        ?.let { (repository, root) -> CheckoutPath(repository, root.relativize(location).joinToString("/")) }

    private fun rootOf(file: Path, depth: Int): Path {
        var root: Path? = file.normalize()
        repeat(depth) { root = root?.parent }
        return root ?: Path.of("")
    }
}
