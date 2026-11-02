package io.github.krosstack.kscope.analyzer.schema.web.facts

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.analyzer.schema.SymbolSite
import io.github.krosstack.kscope.analyzer.schema.TextIndex
import io.github.krosstack.kscope.analyzer.schema.document.DocScalar
import io.github.krosstack.kscope.analyzer.schema.web.WebKinds
import io.github.krosstack.kscope.analyzer.schema.web.manifest.PackageManifest
import io.github.krosstack.kscope.analyzer.schema.web.manifest.PackageManifestReader
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Fact

/**
 * The symbol of each named `package.json`, at its name. It records the version and, when the bundler of the package is
 * known and writes to a directory inside the repository, that directory, whether the config declares it, and the
 * bundler. A manifest without a name leaves no fact.
 */
internal class PackageFacts(private val view: RepositoryView) {
    fun of(manifest: Artifact): List<Fact> {
        val text = view.textOf(manifest)
        val parsed = text?.let(PackageManifestReader::read)
        val placement = view.placementOf(manifest)
        return when {
            text == null -> listOf(WebNotes.unreadable(manifest))
            parsed == null -> listOf(WebNotes.invalidManifest(manifest))
            parsed.name == null -> emptyList()
            placement == null -> listOf(WebNotes.moduleUnknown(manifest))
            else -> listOf(Fact.OfSymbol(symbolOf(manifest, text, parsed, parsed.name, placement)))
        }
    }

    private fun symbolOf(
        manifest: Artifact,
        text: String,
        parsed: PackageManifest,
        name: DocScalar,
        placement: Placement,
    ) = SchemaSymbol(
        id = WebIdentities.packageId(placement.identity, manifest.ref.path.value, name.text),
        kind = WebKinds.npmPackage,
        name = name.text,
        qualifiedName = name.text,
        signature = listOfNotNull(name.text, parsed.version).joinToString(" "),
        container = null,
        declaration = name.span,
        nameSpan = name.span,
        attributes = attributesOf(manifest, parsed),
    ).toSymbol(SymbolSite(manifest.ref, placement.scope, TextIndex(text)))

    private fun attributesOf(manifest: Artifact, parsed: PackageManifest): Map<String, String> {
        val output = view.outputs.outputOf(manifest, parsed)
        return buildMap {
            parsed.version?.let { put(WebKinds.VERSION_ATTRIBUTE, it) }
            output?.let {
                put(WebKinds.OUTPUT_ATTRIBUTE, it.directory)
                put(WebKinds.OUTPUT_DECLARED_ATTRIBUTE, it.isDeclared.toString())
                put(WebKinds.BUNDLER_ATTRIBUTE, it.bundler.id)
            }
        }
    }
}
