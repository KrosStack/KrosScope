package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

/** The contents of the version catalogs of a build, with aliases in the dotted form the accessors use. */
internal object CatalogSection {
    fun describe(project: Project): List<Map<String, Any?>>? {
        val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java) ?: return null
        return catalogs.map(::catalog).sortedBy { it["name"].toString() }
    }

    private fun catalog(catalog: VersionCatalog): Map<String, Any?> = linkedMapOf(
        "name" to catalog.name,
        "versions" to catalog.versionAliases.sorted().associateWith { catalog.findVersion(it).get().displayName },
        "libraries" to catalog.libraryAliases.sorted().associateWith { library(catalog, it) },
        "plugins" to catalog.pluginAliases.sorted().associateWith { plugin(catalog, it) },
        "bundles" to catalog.bundleAliases.sorted().associateWith { bundle(catalog, it) },
    )

    private fun library(catalog: VersionCatalog, alias: String): Map<String, String> {
        val library = catalog.findLibrary(alias).get().get()
        return linkedMapOf("module" to library.module.toString(), "version" to library.versionConstraint.displayName)
    }

    private fun plugin(catalog: VersionCatalog, alias: String): Map<String, String> {
        val plugin = catalog.findPlugin(alias).get().get()
        return linkedMapOf("id" to plugin.pluginId, "version" to plugin.version.displayName)
    }

    private fun bundle(catalog: VersionCatalog, alias: String): List<String> =
        catalog.findBundle(alias).get().get().map { it.module.toString() }
}
