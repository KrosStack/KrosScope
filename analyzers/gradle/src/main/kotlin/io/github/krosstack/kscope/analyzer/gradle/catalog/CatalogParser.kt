package io.github.krosstack.kscope.analyzer.gradle.catalog

import io.github.krosstack.kscope.analyzer.gradle.script.LineIndex
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.TextRange
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlPosition
import org.tomlj.TomlTable

internal class ParsedCatalog(val catalog: VersionCatalog, val problems: List<Diagnostic>)

/** Reads a version catalog with the positions of its entries; entries it cannot read are left out. */
internal class CatalogParser(private val name: String, private val artifact: ArtifactRef, text: String) {
    private val lines = LineIndex(text)
    private val ranges = EntryRanges(text, lines)
    private val toml: TomlParseResult = Toml.parse(text)
    private val values = CatalogToml(toml)

    fun parse(): ParsedCatalog {
        val versions = versionEntries()
        val resolved = versions.mapNotNull { entry -> entry.value?.let { entry.accessor to it } }.toMap()
        val entries = versions + libraryEntries(resolved) + pluginEntries(resolved) + bundleEntries()
        return ParsedCatalog(VersionCatalog(name, artifact, entries), problems())
    }

    private fun versionEntries(): List<CatalogEntry> = values.leaves(CatalogSection.VERSIONS).map { path ->
        val value = values.valueAt(path)
        val text = if (value is TomlTable) values.versionValue(value, emptyMap()) else value as? String
        entryAt(CatalogSection.VERSIONS, path, text, null, emptyList())
    }

    private fun libraryEntries(versions: Map<String, String>): List<CatalogEntry> =
        values.leaves(CatalogSection.LIBRARIES).map { path ->
            val (module, version) = when (val value = values.valueAt(path)) {
                is TomlTable -> values.moduleOf(value) to values.versionOf(value, versions)
                else -> libraryShorthand(value as? String)
            }
            entryAt(CatalogSection.LIBRARIES, path, module, version, emptyList())
        }

    private fun pluginEntries(versions: Map<String, String>): List<CatalogEntry> =
        values.leaves(CatalogSection.PLUGINS).map { path ->
            val (id, version) = when (val value = values.valueAt(path)) {
                is TomlTable -> (value.get("id") as? String) to values.versionOf(value, versions)
                else -> pluginShorthand(value as? String)
            }
            entryAt(CatalogSection.PLUGINS, path, id, version, emptyList())
        }

    private fun bundleEntries(): List<CatalogEntry> = values.leaves(CatalogSection.BUNDLES).map { path ->
        val members = (values.valueAt(path) as TomlArray).toList().filterIsInstance<String>().map(Aliases::accessor)
        entryAt(CatalogSection.BUNDLES, path, null, null, members)
    }

    private fun entryAt(
        section: CatalogSection,
        path: List<String>,
        value: String?,
        version: String?,
        members: List<String>,
    ): CatalogEntry {
        val position: TomlPosition? = values.positionOf(path)
        val declaration = position?.let { ranges.at(it.line()) }
        val fallback = TextRange(1, 1, 1, 1)
        return CatalogEntry(
            section = section,
            alias = path.drop(1).joinToString("."),
            declaration = declaration?.declaration ?: fallback,
            nameRange = declaration?.name ?: fallback,
            value = value,
            version = version,
            members = members,
        )
    }

    private fun problems(): List<Diagnostic> = syntaxErrors() + invalidSections()

    private fun syntaxErrors(): List<Diagnostic> = toml.errors().map { error ->
        Diagnostic(
            severity = DiagnosticSeverity.WARNING,
            code = ReasonCode("gradle.static.catalog-unparsable"),
            message = "The version catalog '$name' has a syntax error: ${error.message?.lineSequence()?.first()}",
            location = SourceLocation(artifact, rangeAt(error.position())),
        )
    }

    private fun invalidSections(): List<Diagnostic> = values.sectionsThatAreNotTables().map { section ->
        Diagnostic(
            severity = DiagnosticSeverity.WARNING,
            code = ReasonCode("gradle.static.catalog-section-invalid"),
            message = "The version catalog '$name' has a '${section.key}' that is not a table; it is ignored.",
            location = values.positionOf(listOf(section.key))?.let { SourceLocation(artifact, rangeAt(it)) },
        )
    }

    private fun rangeAt(position: TomlPosition): TextRange {
        val start = lines.offsetOf(position.line(), position.column()) ?: 0
        return lines.rangeOf(start, start)
    }
}
