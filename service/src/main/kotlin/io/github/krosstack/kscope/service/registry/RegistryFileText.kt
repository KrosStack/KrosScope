package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.engine.RegisteredRepository
import java.io.File
import java.nio.file.Path
import java.util.Locale

/**
 * Writes `workspaces.toml`: one table per workspace, its repositories as inline tables and its artifact folders as
 * strings, in a stable order. Paths are written with `/` as separator, and a backslash that remains is escaped, so that
 * a path of any system reads back unchanged.
 */
internal object RegistryFileText {
    const val WORKSPACE = "workspace"
    const val PATH = "path"
    const val REPOSITORIES = "repositories"
    const val NAME = "name"
    const val ARTIFACT_FOLDERS = "artifact-folders"

    fun render(all: Map<String, RegisteredWorkspace>): String = buildString {
        append("# Registered with a workspace by `kscope workspace add` and `kscope workspace artifacts add`. ")
        append("Change it with those commands.\n")
        all.filterValues { !it.isEmpty }.toSortedMap().forEach { (workspace, registered) ->
            append("\n[[$WORKSPACE]]\n")
            append("$PATH = ${quoted(workspace)}\n")
            append(repositories(registered.repositories))
            append(folders(registered.artifactFolders))
        }
    }

    private fun repositories(repositories: List<RegisteredRepository>): String = buildString {
        append("$REPOSITORIES = [\n")
        repositories.sortedBy { it.name }.forEach { repository ->
            append("  { $NAME = ${quoted(repository.name)}, $PATH = ${quoted(slashed(repository.root))} },\n")
        }
        append("]\n")
    }

    private fun folders(folders: List<Path>): String = buildString {
        if (folders.isEmpty()) return@buildString
        append("$ARTIFACT_FOLDERS = [\n")
        folders.forEach { append("  ${quoted(slashed(it))},\n") }
        append("]\n")
    }

    private fun slashed(path: Path): String = path.toString().replace(File.separatorChar, '/')

    private fun quoted(value: String): String = "\"" + value.map(::escaped).joinToString("") + "\""

    private fun escaped(character: Char): String = when {
        character == '\\' -> "\\\\"
        character == '"' -> "\\\""
        character.code < SPACE || character.code == DELETE -> "\\u%04x".format(Locale.ROOT, character.code)
        else -> character.toString()
    }

    private const val SPACE = 0x20
    private const val DELETE = 0x7f
}
