package io.github.krosstack.kscope.analyzer.gradle.catalog

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.TextRange

internal enum class CatalogSection(val key: String, val symbolKind: String, val accessorPrefix: String?) {
    VERSIONS("versions", "gradle.catalog.version", "versions"),
    LIBRARIES("libraries", "gradle.catalog.library", null),
    BUNDLES("bundles", "gradle.catalog.bundle", "bundles"),
    PLUGINS("plugins", "gradle.catalog.plugin", "plugins"),
}

/**
 * An entry of a version catalog. [accessor] is the alias in the form Gradle's type-safe accessors use, with `-`, `_`
 * and `.` all separating segments. [value] is the version for a version entry, `group:name` for a library and the id
 * for a plugin; [version] is the version a library or plugin resolves to, if it declares one.
 */
internal data class CatalogEntry(
    val section: CatalogSection,
    val alias: String,
    val declaration: TextRange,
    val nameRange: TextRange,
    val value: String?,
    val version: String?,
    val members: List<String>,
) {
    val accessor: String = Aliases.accessor(alias)
}

internal class VersionCatalog(val name: String, val artifact: ArtifactRef, val entries: List<CatalogEntry>) {
    private val byAccessor = entries.associateBy { it.section to it.accessor }

    fun entry(section: CatalogSection, accessor: String): CatalogEntry? = byAccessor[section to accessor]
}

/** A catalog together with the build root whose symbols hold its entries. */
internal class RegisteredCatalog(val catalog: VersionCatalog, val owner: BuildRootId)

internal object Aliases {
    fun accessor(alias: String): String = alias.split('-', '_', '.').filter { it.isNotEmpty() }.joinToString(".")
}
