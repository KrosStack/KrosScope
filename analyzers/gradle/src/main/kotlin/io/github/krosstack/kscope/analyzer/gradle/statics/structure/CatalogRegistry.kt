package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogParser
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogResolver
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogSymbols
import io.github.krosstack.kscope.analyzer.gradle.catalog.ParsedCatalog
import io.github.krosstack.kscope.analyzer.gradle.catalog.RegisteredCatalog
import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.spi.WorkspaceFiles

private const val CATALOG_SUFFIX = ".versions.toml"
private const val DEFAULT_DIRECTORY = "gradle"

/**
 * The version catalogs of a workspace. A catalog belongs to the build whose directory holds it, and a build reads the
 * catalogs in its `gradle` directory under their file names and the ones its settings import under the given names.
 */
internal class CatalogRegistry(private val plans: List<BuildPlan>, catalogs: List<Artifact>, files: WorkspaceFiles) {
    private val parsed: List<ParsedCatalog> = catalogs.map { artifact ->
        val name = artifact.ref.path.value.substringAfterLast('/').removeSuffix(CATALOG_SUFFIX)
        CatalogParser(name, artifact.ref, files.read(artifact.ref).toString(Charsets.UTF_8)).parse()
    }

    private val registered: Map<String, RegisteredCatalog> = parsed.mapNotNull { entry ->
        val path = entry.catalog.artifact.path.value
        ownerOf(RepoPaths.parentOf(path))?.let { path to RegisteredCatalog(entry.catalog, it) }
    }.toMap()

    val problems: List<Diagnostic> = parsed.flatMap { it.problems }

    val symbols: List<Fact> = registered.values.flatMap(CatalogSymbols::factsOf)

    fun resolverFor(plan: BuildPlan): CatalogResolver {
        val directory = plan.root.directory.value
        val defaultDirectory = defaultsOf(directory)
        val defaults = registered.values
            .filter { RepoPaths.parentOf(it.catalog.artifact.path.value) == defaultDirectory }
            .associateBy { it.catalog.name }
        val imported = plan.settings?.catalogImports.orEmpty().mapNotNull { import ->
            RepoPaths.resolve(directory, import.file)?.let(registered::get)?.let { import.name to it }
        }.toMap()
        return CatalogResolver(defaults + imported)
    }

    private fun defaultsOf(directory: String) = RepoPaths.join(directory, DEFAULT_DIRECTORY)

    private fun ownerOf(directory: String) = plans
        .filter { RepoPaths.isWithin(directory, it.root.directory.value) }
        .maxByOrNull { it.root.directory.value.length }
        ?.root?.id
}
