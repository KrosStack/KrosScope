package io.github.krosstack.kscope.analyzer.gradle.statics.dependency

import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogLookup
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogReference
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogResolver
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogSection
import io.github.krosstack.kscope.analyzer.gradle.script.namedArgument
import io.github.krosstack.kscope.analyzer.gradle.script.unquote
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** What a dependency notation turned out to be. */
internal sealed interface Notation {
    class Project(val path: String) : Notation

    /** A library: its `group:name`, its version if the notation gives one, and the catalog entry it came from. */
    class Library(val module: String, val version: String?, val reference: CatalogReference?, val heuristic: Boolean) :
        Notation

    class Bundle(val reference: CatalogReference, val members: List<Library>, val heuristic: Boolean) : Notation

    /** A catalog alias that names no entry. */
    class MissingAlias(val missing: CatalogLookup.Missing, val expression: String) : Notation

    /** The files of `files(...)` or `fileTree(...)` whose paths could be read. */
    class Files(val files: List<DeclaredFile>) : Notation

    /** A notation that is well formed but names no module, such as `gradleApi()`. */
    data object Ignored : Notation

    class Unknown(val expression: String) : Notation
}

private val wrappers = Regex("""(?:platform|enforcedPlatform|testFixtures)\((.*)\)""")
private val projectNotation = Regex("""project\(\s*(?:path\s*[:=]\s*)?["'](:[^"']*)["'][^)]*\)""")
private val accessorNotation = Regex("""projects\.([A-Za-z0-9_.]+)""")
private val kotlinNotation = Regex("""kotlin\(\s*["']([^"']+)["'][^)]*\)""")
private val ignoredNotations = Regex("""(?:files|fileTree|gradleApi|gradleKotlinDsl|localGroovy)\(.*\)""")

/** Parses the notation of a dependency declaration, in either DSL. */
internal class DependencyNotations(
    private val catalogs: CatalogResolver,
    private val projectAccessors: Map<String, String>,
) {
    fun parse(call: ScriptCall, expression: String?): Notation =
        namedCoordinate(call) ?: expression?.let { parse(it.trim()) } ?: Notation.Ignored

    private fun parse(text: String): Notation {
        val fallback = if (ignoredNotations.matches(text)) Notation.Ignored else Notation.Unknown(text)
        return wrappers.matchEntire(text)?.let { parse(it.groupValues[1].trim()) }
            ?: FileNotations.parse(text)?.takeIf { it.isNotEmpty() }?.let { Notation.Files(it) }
            ?: unquote(text)?.let(::coordinate)
            ?: projectOf(text)
            ?: catalogNotation(text)
            ?: fallback
    }

    private fun projectOf(text: String): Notation? =
        projectNotation.matchEntire(text)?.let { Notation.Project(it.groupValues[1]) }
            ?: accessorNotation.matchEntire(text)?.let { projectFromAccessor(it.groupValues[1], text) }
            ?: kotlinNotation.matchEntire(text)?.let(::kotlinModule)

    private fun kotlinModule(match: MatchResult): Notation =
        Notation.Library("org.jetbrains.kotlin:kotlin-${match.groupValues[1]}", null, null, false)

    private fun projectFromAccessor(accessor: String, text: String): Notation =
        projectAccessors[accessor]?.let { Notation.Project(it) } ?: Notation.Unknown(text)

    private fun coordinate(text: String): Notation {
        val parts = text.substringBefore('@').split(':')
        return if (parts.size >= 2 && parts.all { it.isNotEmpty() }) {
            Notation.Library("${parts[0]}:${parts[1]}", parts.getOrNull(2), null, false)
        } else {
            Notation.Unknown(text)
        }
    }

    private fun namedCoordinate(call: ScriptCall): Notation? {
        val group = call.namedArgument("group")?.literal
        val name = call.namedArgument("name")?.literal
        return if (group != null && name != null) {
            Notation.Library("$group:$name", call.namedArgument("version")?.literal, null, false)
        } else {
            null
        }
    }

    private fun catalogNotation(text: String): Notation? {
        val byAccess = catalogs.lookup(text)
        val lookup = if (byAccess is CatalogLookup.NotACatalog) catalogs.lookupByName(text) else byAccess
        val heuristic = byAccess is CatalogLookup.NotACatalog
        return when (lookup) {
            is CatalogLookup.Found -> entryNotation(lookup.reference, heuristic, text)
            is CatalogLookup.Missing -> Notation.MissingAlias(lookup, text)
            CatalogLookup.NotACatalog -> null
        }
    }

    private fun entryNotation(reference: CatalogReference, heuristic: Boolean, text: String): Notation =
        when (reference.entry.section) {
            CatalogSection.LIBRARIES -> libraryOf(reference, heuristic) ?: Notation.Unknown(text)
            CatalogSection.BUNDLES -> Notation.Bundle(reference, bundleMembers(reference, heuristic), heuristic)
            else -> Notation.Unknown(text)
        }

    private fun libraryOf(reference: CatalogReference, heuristic: Boolean): Notation.Library? =
        reference.entry.value?.let { Notation.Library(it, reference.entry.version, reference, heuristic) }

    private fun bundleMembers(bundle: CatalogReference, heuristic: Boolean): List<Notation.Library> =
        bundle.entry.members.mapNotNull { member ->
            bundle.registered.catalog.entry(CatalogSection.LIBRARIES, member)
                ?.let { libraryOf(CatalogReference(bundle.registered, it), heuristic) }
        }
}
