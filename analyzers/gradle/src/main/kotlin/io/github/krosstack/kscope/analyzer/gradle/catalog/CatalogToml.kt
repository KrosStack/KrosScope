package io.github.krosstack.kscope.analyzer.gradle.catalog

import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlPosition
import org.tomlj.TomlTable

private val versionKeys = listOf("require", "strictly", "prefer")

private fun isVersion(value: Any) = value is String || (value is TomlTable && versionKeys.any(value::contains))

private fun isLibrary(value: Any) =
    value is String || (value is TomlTable && (value.contains("module") || value.contains("group")))

private fun isPlugin(value: Any) = value is String || (value is TomlTable && value.contains("id"))

/**
 * Reads values out of a parsed catalog in the notations Gradle accepts: strings, inline tables and dotted keys, with
 * versions given directly, by `version.ref` or as a rich version.
 */
internal class CatalogToml(private val toml: TomlParseResult) {
    fun leaves(section: CatalogSection): List<List<String>> {
        val isLeaf: (Any) -> Boolean = when (section) {
            CatalogSection.VERSIONS -> ::isVersion
            CatalogSection.LIBRARIES -> ::isLibrary
            CatalogSection.PLUGINS -> ::isPlugin
            CatalogSection.BUNDLES -> { value -> value is TomlArray }
        }
        return walk(listOf(section.key), isLeaf)
    }

    fun valueAt(path: List<String>): Any? = toml.get(path)

    fun sectionsThatAreNotTables(): List<CatalogSection> = CatalogSection.entries.filter { section ->
        val value = toml.get(listOf(section.key))
        value != null && value !is TomlTable
    }

    fun positionOf(path: List<String>): TomlPosition? = toml.inputPositionOf(path)

    fun moduleOf(table: TomlTable): String? {
        val module = table.get("module") as? String
        val group = table.get("group") as? String
        val name = table.get("name") as? String
        return module ?: if (group != null && name != null) "$group:$name" else null
    }

    fun versionOf(table: TomlTable, versions: Map<String, String>): String? =
        when (val version = table.get("version")) {
            is String -> version
            is TomlTable -> versionValue(version, versions)
            else -> null
        }

    fun versionValue(table: TomlTable, versions: Map<String, String>): String? =
        (table.get("ref") as? String)?.let { versions[Aliases.accessor(it)] }
            ?: versionKeys.firstNotNullOfOrNull { table.get(it) as? String }

    private fun walk(path: List<String>, isLeaf: (Any) -> Boolean): List<List<String>> =
        (toml.get(path) as? TomlTable)?.keySet().orEmpty().flatMap { key ->
            val child = path + key
            when (val value = toml.get(child)) {
                null -> emptyList()
                else -> descend(child, value, isLeaf)
            }
        }

    private fun descend(path: List<String>, value: Any, isLeaf: (Any) -> Boolean): List<List<String>> = when {
        isLeaf(value) -> listOf(path)
        value is TomlTable -> walk(path, isLeaf)
        else -> emptyList()
    }
}

/** `group:name:version` and `group:name` notations; anything else is taken as the module itself. */
internal fun libraryShorthand(notation: String?): Pair<String?, String?> {
    val parts = notation?.split(':').orEmpty()
    return if (parts.size >= 2) "${parts[0]}:${parts[1]}" to parts.getOrNull(2) else notation to null
}

/** `id:version` notation of a plugin. */
internal fun pluginShorthand(notation: String?): Pair<String?, String?> =
    notation?.substringBefore(':') to notation?.substringAfter(':', "")?.takeIf { it.isNotEmpty() }
