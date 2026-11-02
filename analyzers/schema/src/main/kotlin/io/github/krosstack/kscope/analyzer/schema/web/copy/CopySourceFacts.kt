package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaSymbol
import io.github.krosstack.kscope.analyzer.schema.SymbolSite
import io.github.krosstack.kscope.analyzer.schema.TextIndex
import io.github.krosstack.kscope.analyzer.schema.generator.specs.ScriptLocations
import io.github.krosstack.kscope.analyzer.schema.web.WebKinds
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolDescriptor

/**
 * The symbol of each path that a copy block of a build script names. It belongs to the module of the script and to no
 * source set, and sits at the `from` call. It is named after the path without its `$rootDir` or `$projectDir`, and
 * keeps the path as the script writes it as its signature. When the path lies in the checkout of a repository of the
 * workspace, the symbol records that repository and the normalized path in it, never a location on disk.
 */
internal class CopySourceFacts(
    private val script: Artifact,
    private val placement: Placement,
    text: String,
    private val locations: ScriptLocations,
) {
    private val site = SymbolSite(script.ref, placement.scope, TextIndex(text))
    private val occurrences = HashMap<String, Int>()

    fun of(source: CopySource): Fact = Fact.OfSymbol(symbolOf(source))

    private fun symbolOf(source: CopySource): Symbol = SchemaSymbol(
        id = placement.identity.idOf(descriptorOf(source)),
        kind = WebKinds.copySource,
        name = source.path.relative.ifEmpty { source.path.text },
        qualifiedName = "${script.ref.path.value}#${source.task}.from",
        signature = source.path.text,
        container = null,
        declaration = source.call,
        nameSpan = source.name,
        attributes = attributesOf(source),
        evidence = EvidenceClass.BUILD_MODEL,
    ).toSymbol(site)

    private fun attributesOf(source: CopySource): Map<String, String> {
        val place = locations.inCheckout(source.path, script.ref.repository, placement.module)
        return buildMap {
            put(WebKinds.COPY_TASK_ATTRIBUTE, source.task)
            source.into?.let { put(WebKinds.COPY_INTO_ATTRIBUTE, it) }
            place?.let {
                put(WebKinds.COPY_REPOSITORY_ATTRIBUTE, it.repository.value)
                put(WebKinds.COPY_PATH_ATTRIBUTE, it.path)
            }
        }
    }

    private fun descriptorOf(source: CopySource): SymbolDescriptor {
        val property = "${source.task}.from"
        val descriptor = SymbolDescriptor().meta(script.ref.path.value).meta(property).term(source.path.text)
        val ordinal = occurrences.merge("$property ${source.path.text}", 1, Int::plus) ?: 1
        return if (ordinal == 1) descriptor else descriptor.term(ordinal.toString())
    }
}
