package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.ClientPaths
import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.service.lookup.IndexLookup
import java.nio.file.Files
import java.nio.file.Path

internal sealed interface IndexSource {
    data object Stated : IndexSource

    data object Missing : IndexSource

    class Parent(val folder: Path) : IndexSource
}

internal fun ResolvedOptions.inNearestIndex(
    execution: CommandExecution,
    lookup: IndexLookup = execution.lookup,
): ResolvedOptions {
    val served = execution.serverWorkspace
    val root = if (served == null) lookup.nearest(workspace) else ancestorAt(workspace, served) ?: workspace
    return when (root) {
        null -> copy(source = IndexSource.Missing)
        workspace -> this
        else -> copy(workspace = root, source = IndexSource.Parent(workspace))
    }
}

private fun ancestorAt(folder: Path, served: Path): Path? =
    generateSequence(folder) { it.parent }.firstOrNull { Files.isDirectory(it) && it.toRealPath() == served }

internal fun ResolvedOptions.parentIndex(): String? =
    (source as? IndexSource.Parent)?.let { ClientPaths.indexLocation(workspace) }

internal fun ResolvedOptions.askedFrom(): String? =
    (source as? IndexSource.Parent)?.let { workspace.relativize(it.folder).joinToString("/") }

internal fun ResolvedOptions.fromFolder(argument: String): String {
    val folder = (source as? IndexSource.Parent)?.folder
    val path = argument.replace(LINE, "")
    val file = folder?.let { fileIn(it, path) }?.takeIf { it.startsWith(workspace) }
    return file?.let { workspace.relativize(it).joinToString("/") + argument.substring(path.length) } ?: argument
}

private fun fileIn(folder: Path, path: String): Path? =
    runCatching { folder.resolve(path).normalize() }.getOrNull()?.takeIf { Files.isRegularFile(it) }

private val LINE = Regex(":[0-9]+$")
