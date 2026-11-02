package io.github.krosstack.kscope.service.registry

import io.github.krosstack.kscope.engine.RegisteredRepository
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Reads `workspaces.toml`. The grammar is strict: a key that is not known, a value of another type and a path that
 * the system cannot read make the whole file invalid, and a file without `artifact-folders` is read as it always was.
 */
internal object RegistryFileParser {
    private val workspaceKeys = setOf(RegistryFileText.PATH, RegistryFileText.REPOSITORIES)

    /** What the file registers, by the key of each workspace; null when the file is not valid. */
    fun parse(text: String): Map<String, RegisteredWorkspace>? {
        val toml = Toml.parse(text)
        val valid = !toml.hasErrors() && toml.keySet().all { it == RegistryFileText.WORKSPACE }
        val workspaces = (toml.get(RegistryFileText.WORKSPACE) as? TomlArray).takeIf { valid }
        return when {
            !valid -> null
            workspaces == null -> emptyMap<String, RegisteredWorkspace>().takeIf { toml.isEmpty }
            else -> allOrNone(tables(workspaces)?.map(::workspaceOf))?.toMap()
        }
    }

    private fun workspaceOf(table: TomlTable): Pair<String, RegisteredWorkspace>? {
        val path = table.get(RegistryFileText.PATH) as? String
        val array = table.get(RegistryFileText.REPOSITORIES) as? TomlArray
        val repositories = allOrNone(array?.let(::tables)?.map(::repositoryOf))
        val folders = foldersOf(table)
        val known = table.keySet() - RegistryFileText.ARTIFACT_FOLDERS == workspaceKeys
        val registered = if (repositories != null && folders != null && known) {
            RegisteredWorkspace(repositories, folders)
        } else {
            null
        }
        return if (path != null && registered != null) path to registered else null
    }

    private fun foldersOf(table: TomlTable): List<Path>? {
        val array = table.get(RegistryFileText.ARTIFACT_FOLDERS)
        return when (array) {
            null -> emptyList()
            !is TomlArray -> null
            else -> allOrNone((0 until array.size()).map { (array.get(it) as? String)?.let(::pathOf) })
        }
    }

    private fun repositoryOf(table: TomlTable): RegisteredRepository? {
        val name = table.get(RegistryFileText.NAME) as? String
        val path = table.get(RegistryFileText.PATH) as? String
        val keys = table.keySet() == setOf(RegistryFileText.NAME, RegistryFileText.PATH)
        return if (name == null || path == null || !keys) null else RegisteredRepository(name, Path.of(path))
    }

    private fun pathOf(text: String): Path? = try {
        Path.of(text)
    } catch (_: InvalidPathException) {
        null
    }

    private fun <T : Any> allOrNone(items: List<T?>?): List<T>? =
        items?.takeIf { list -> list.none { it == null } }?.filterNotNull()

    private fun tables(array: TomlArray): List<TomlTable>? {
        val values = (0 until array.size()).map(array::get)
        return if (values.all { it is TomlTable }) values.filterIsInstance<TomlTable>() else null
    }
}
