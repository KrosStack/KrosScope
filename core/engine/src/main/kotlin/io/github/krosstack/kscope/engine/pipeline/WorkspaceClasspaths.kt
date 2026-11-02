package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.discovery.toRepoPathString
import io.github.krosstack.kscope.model.SourceSetId
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** A checkout [root] and the files below it, relative to it, that the repository keeps, whatever the scope. */
internal class CheckoutFiles(val root: Path, val files: Set<String>)

/**
 * Drops the classpath entries that lie inside a repository of the workspace without being one of its files, such as
 * the class directories and jars other modules build or the generated `R.jar`. Workspace code is analysed from its
 * source, and the output of a build changes without its inputs changing, so it neither belongs on a classpath nor in
 * the fingerprint of a unit. A binary that the repository keeps, such as a vendored jar, is not build output and
 * stays.
 */
internal class WorkspaceClasspaths(private val checkouts: List<CheckoutFiles>) {
    fun withoutBuildOutput(classpaths: Map<SourceSetId, List<String>>): Map<SourceSetId, List<String>> =
        classpaths.mapValues { (_, entries) -> entries.filterNot(::isBuildOutput) }
            .filterValues { it.isNotEmpty() }

    private fun isBuildOutput(entry: String): Boolean = try {
        val path = Path.of(entry).toAbsolutePath().normalize()
        val containing = checkouts.filter { path.startsWith(it.root) }
        containing.isNotEmpty() && containing.none { path.toRepoPathString(it.root) in it.files }
    } catch (_: InvalidPathException) {
        false
    }
}
