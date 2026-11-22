package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.ClientPaths
import io.github.krosstack.kscope.cli.text.format.Hints
import io.github.krosstack.kscope.service.lookup.RepositoryNarrowing
import io.github.krosstack.kscope.service.lookup.SearchOrigin
import java.nio.file.Path

internal object IndexNotes {
    private const val NAMED = 5

    fun parent(root: Path, origin: SearchOrigin = SearchOrigin(), query: String = ""): String =
        "Using the index of a parent folder: ${ClientPaths.indexLocation(root)}. " +
            scope(root, origin, query) + remarks(root, origin, query)

    private fun scope(root: Path, origin: SearchOrigin, query: String): String {
        val narrowing = origin.narrowing
        val widened = origin.widenedFrom
        return when {
            narrowing != null -> "Paths are relative to ${slashed(root)}. Answer limited to ${limit(narrowing)}; " +
                "pass --workspace ${slashed(root)} for the whole workspace."

            widened != null -> "The answer covers the whole workspace ${slashed(root)}, and paths are relative to " +
                "it. Nothing in ${limit(widened)} matches \"$query\", so the search was widened to the whole workspace."

            else -> "The answer covers the whole workspace ${slashed(root)}, and paths are relative to it."
        }
    }

    private fun remarks(root: Path, origin: SearchOrigin, query: String): String {
        val outside = origin.outside?.let { " \"$query\" lies in ${limit(it)}, outside that limit." }.orEmpty()
        val incomplete = if (origin.incomplete) " " + stopped(root, origin.narrowing) else ""
        return outside + incomplete
    }

    private fun stopped(root: Path, narrowing: RepositoryNarrowing?): String {
        val where = narrowing?.let { limit(it) } ?: "the workspace"
        return "The search in $where stopped at its limit of candidates and may be incomplete; " +
            "pass --workspace ${slashed(root)} to search everywhere."
    }

    private fun limit(repository: RepositoryNarrowing): String =
        if (repository.atRoot) "the repository ${repository.name} at the workspace root" else "${repository.name}/"

    fun chosen(name: String, repository: String, root: Path): String =
        "Using the declaration of \"$name\" in $repository, the repository of the current folder. " +
            "Other repositories declare this name too; --workspace ${slashed(root)} lists them all."

    fun covered(outer: Path, folder: Path): String =
        "An index in a parent folder already covers this folder: ${ClientPaths.indexLocation(outer)}. " +
            "This scan makes a separate index in ${ClientPaths.indexLocation(folder)}."

    fun missing(folder: Path, below: List<Path>): String {
        if (below.isEmpty()) return Hints.NO_INDEX
        val names = below.take(NAMED).joinToString(", ") { slashed(folder.relativize(it)) }
        val more = if (below.size > NAMED) " and ${below.size - NAMED} more" else ""
        return "${Hints.NO_INDEX}\nFolders below with an index: $names$more. Pass one with --workspace."
    }

    private fun slashed(path: Path): String = path.toString().replace('\\', '/')
}
