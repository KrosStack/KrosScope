package io.github.krosstack.kscope.analyzer.gradle.catalog

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility

/**
 * The symbols of version catalog entries: unit is the build root that holds the catalog, the source set is
 * not applicable, and the descriptor is `gradle/catalog/<catalog>/<section>/<alias>.`.
 */
internal object CatalogSymbols {
    private const val NOT_APPLICABLE = "."

    fun idOf(registered: RegisteredCatalog, entry: CatalogEntry): SymbolId {
        val descriptor = SymbolDescriptor().namespace("gradle").namespace("catalog")
            .namespace(registered.catalog.name).namespace(entry.section.key).term(entry.accessor)
        return SymbolIdentity(SymbolEcosystem.GRADLE, registered.owner.value, NOT_APPLICABLE, descriptor).toSymbolId()
    }

    fun factsOf(registered: RegisteredCatalog): List<Fact> =
        registered.catalog.entries.map { Fact.OfSymbol(symbolOf(registered, it)) }

    private fun symbolOf(registered: RegisteredCatalog, entry: CatalogEntry): Symbol {
        val artifact = registered.catalog.artifact
        return Symbol(
            id = idOf(registered, entry),
            kind = SymbolKind(entry.section.symbolKind, SymbolCategory.BUILD),
            name = entry.accessor,
            qualifiedName = qualifiedNameOf(registered.catalog.name, entry),
            signature = signatureOf(entry),
            container = null,
            visibility = Visibility.PUBLIC,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            declaration = SourceLocation(artifact, entry.declaration),
            nameLocation = SourceLocation(artifact, entry.nameRange),
            documentation = null,
            scope = Scope(),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = Certainty.EXACT,
            attributes = attributesOf(registered, entry),
        )
    }

    private fun qualifiedNameOf(catalog: String, entry: CatalogEntry): String =
        listOfNotNull(catalog, entry.section.accessorPrefix, entry.accessor).joinToString(".")

    private fun signatureOf(entry: CatalogEntry): String {
        if (entry.section == CatalogSection.BUNDLES) return entry.members.joinToString(", ", "[", "]")
        return listOfNotNull(entry.value, entry.version.takeIf { entry.section != CatalogSection.VERSIONS })
            .joinToString(":")
    }

    private fun attributesOf(registered: RegisteredCatalog, entry: CatalogEntry): Map<String, String> {
        val version = entry.version?.takeIf { entry.section != CatalogSection.VERSIONS }
        return listOfNotNull(
            "alias" to entry.alias,
            "catalog" to registered.catalog.name,
            entry.value?.let { "value" to it },
            version?.let { "version" to it },
        ).toMap()
    }
}
