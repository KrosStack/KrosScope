package io.github.krosstack.kscope.benchmark.score.build

import io.github.krosstack.kscope.benchmark.gold.GoldBuildModel
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind

private const val CONFIGURATION = "configuration"

private val catalogKinds = mapOf(
    "gradle.catalog.library" to "library",
    "gradle.catalog.plugin" to "plugin",
    "gradle.catalog.bundle" to "bundle",
)

/** The dependencies between projects, and the entries of the version catalog. */
internal class DependencyDimensions(private val index: GraphIndex) : DimensionSource {
    override fun dimensions(gold: GoldBuildModel): List<Dimension> = listOf(
        Dimension("project dependencies", expectedProjects(gold), producedProjects()),
        Dimension("catalog entries", expectedCatalog(gold), producedCatalog()),
    )

    private fun expectedProjects(gold: GoldBuildModel) =
        gold.projectDependencies.map { edge(it.from, it.to, it.configuration) }

    private fun producedProjects(): List<Item> = index.relationships
        .filter { it.kind == RelationKind.DEPENDS_ON }
        .mapNotNull { relationship ->
            val from = pathOf(relationship.from)
            val to = index.resolvedTarget(relationship)?.let(::pathOf)
            if (from == null || to == null) null else edge(from, to, relationship.attributes[CONFIGURATION].orEmpty())
        }

    private fun expectedCatalog(gold: GoldBuildModel): List<Item> =
        gold.catalog.libraries.keys.map { entry("library", it) } +
            gold.catalog.plugins.keys.map { entry("plugin", it) } +
            gold.catalog.bundles.keys.map { entry("bundle", it) }

    private fun producedCatalog(): List<Item> = index.symbols.mapNotNull { symbol ->
        catalogKinds[symbol.kind.id]?.let { entry(it, symbol.name) }
    }

    private fun pathOf(node: NodeRef): String? =
        (node as? NodeRef.OfModule)?.let { index.modulesById[it.id.value] }?.let(index::goldPathOf)

    private fun edge(from: String, to: String, configuration: String) =
        Item("$from|$to|$configuration", "$from -> $to ($configuration)")

    private fun entry(kind: String, alias: String) = Item("$kind|$alias", "catalog $kind $alias")
}
