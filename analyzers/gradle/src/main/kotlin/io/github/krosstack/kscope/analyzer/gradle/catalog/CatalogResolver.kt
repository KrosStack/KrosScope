package io.github.krosstack.kscope.analyzer.gradle.catalog

import io.github.krosstack.kscope.model.SymbolId

/** An entry of a registered catalog and the symbol that stands for it. */
internal class CatalogReference(val registered: RegisteredCatalog, val entry: CatalogEntry) {
    val symbol: SymbolId = CatalogSymbols.idOf(registered, entry)
}

/** What an expression that may read a version catalog turned out to be. */
internal sealed interface CatalogLookup {
    class Found(val reference: CatalogReference) : CatalogLookup

    /** The expression starts with a catalog name and a section, but names no entry. */
    class Missing(val catalog: String, val accessor: String) : CatalogLookup

    data object NotACatalog : CatalogLookup
}

private val accessPath = Regex("""[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*""")
private val lookupCall = Regex("""\.find(Library|Bundle|Plugin|Version)\(\s*["']([^"']+)["']\s*\)""")

/** Resolves the catalogs a build can read, by their name in the build. */
internal class CatalogResolver(private val catalogs: Map<String, RegisteredCatalog>) {
    val isEmpty: Boolean get() = catalogs.isEmpty()

    fun registered(): Collection<RegisteredCatalog> = catalogs.values

    /** `libs.kotlin.test`, `libs.bundles.views`, `libs.plugins.android.library` and `libs.versions.kotlin.get()`. */
    fun lookup(expression: String): CatalogLookup {
        val text = expression.trim()
        val path = accessPath.matchAt(text, 0)?.value
        val segments = path?.split('.')?.let { if (text.startsWith("(", path.length)) it.dropLast(1) else it }
        val registered = segments?.firstOrNull()?.let(catalogs::get)
        return if (segments == null || registered == null) {
            CatalogLookup.NotACatalog
        } else {
            resolve(registered, segments.drop(1))
        }
    }

    /** `catalog.findLibrary("junit")` and its siblings, which name the catalog only through a variable. */
    fun lookupByName(expression: String): CatalogLookup {
        val match = lookupCall.find(expression)
        val registered = catalogs["libs"] ?: catalogs.values.singleOrNull()
        return if (match == null || registered == null) {
            CatalogLookup.NotACatalog
        } else {
            found(registered, sectionOf(match.groupValues[1]), Aliases.accessor(match.groupValues[2]))
        }
    }

    private fun sectionOf(kind: String): CatalogSection = when (kind) {
        "Library" -> CatalogSection.LIBRARIES
        "Bundle" -> CatalogSection.BUNDLES
        "Plugin" -> CatalogSection.PLUGINS
        else -> CatalogSection.VERSIONS
    }

    private fun resolve(registered: RegisteredCatalog, segments: List<String>): CatalogLookup {
        val prefix = segments.firstOrNull()
        val section = CatalogSection.entries.firstOrNull { prefix != null && it.accessorPrefix == prefix }
            ?: CatalogSection.LIBRARIES
        val accessor = if (section == CatalogSection.LIBRARIES) segments else segments.drop(1)
        if (accessor.isEmpty()) return CatalogLookup.NotACatalog
        val longest = accessor.indices.reversed().map { accessor.take(it + 1).joinToString(".") }
            .firstOrNull { registered.catalog.entry(section, it) != null }
        return found(registered, section, longest ?: accessor.joinToString("."))
    }

    private fun found(registered: RegisteredCatalog, section: CatalogSection, accessor: String): CatalogLookup {
        val entry = registered.catalog.entry(section, accessor)
        return if (entry != null) {
            CatalogLookup.Found(CatalogReference(registered, entry))
        } else {
            CatalogLookup.Missing(registered.catalog.name, accessor)
        }
    }
}
