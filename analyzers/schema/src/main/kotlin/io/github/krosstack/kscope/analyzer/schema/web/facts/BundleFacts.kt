package io.github.krosstack.kscope.analyzer.schema.web.facts

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.web.RepoPaths
import io.github.krosstack.kscope.analyzer.schema.web.SourceMapDetector
import io.github.krosstack.kscope.analyzer.schema.web.WebKinds
import io.github.krosstack.kscope.analyzer.schema.web.map.SourceMap
import io.github.krosstack.kscope.analyzer.schema.web.map.SourceMapReader
import io.github.krosstack.kscope.analyzer.schema.web.map.SourceResolver
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol

/**
 * The facts of a source map that a build left among its outputs: the symbol of the file it maps, and a dependency from
 * that symbol on the symbol of each hand-written file the map names as a source. The file it maps is the `file` of the
 * map, else the map without its suffix. A source that is not a hand-written script of the repository leaves no
 * fact, and neither does a map whose file is not an artifact. A map that cannot be read, or is none, leaves a note.
 */
internal class BundleFacts(private val view: RepositoryView) {
    fun of(map: Artifact): List<Fact> {
        val text = view.textOf(map)
        val parsed = text?.let(SourceMapReader::read)
        return when {
            text == null -> listOf(WebNotes.unreadableSourceMap(map))
            parsed == null -> listOf(WebNotes.invalidSourceMap(map))
            else -> view.artifacts[bundlePathOf(map, parsed)]?.let { factsOf(map, parsed, it) }.orEmpty()
        }
    }

    private fun bundlePathOf(map: Artifact, parsed: SourceMap): String {
        val path = map.ref.path.value
        return parsed.file?.let { RepoPaths.resolve(RepoPaths.directoryOf(path), it) }
            ?: path.removeSuffix(SourceMapDetector.SUFFIX)
    }

    private fun factsOf(map: Artifact, parsed: SourceMap, bundle: Artifact): List<Fact> {
        val placement = view.placementOf(bundle)
        val text = view.textOf(bundle)
        return when {
            placement == null -> listOf(WebNotes.moduleUnknown(bundle))
            text == null -> listOf(WebNotes.unreadable(bundle))
            else -> linked(map, parsed, bundle, text, placement)
        }
    }

    private fun linked(
        map: Artifact,
        parsed: SourceMap,
        bundle: Artifact,
        text: String,
        placement: Placement,
    ): List<Fact> {
        val symbol = FileSymbols.of(bundle, text, placement, WebKinds.bundleFile, EvidenceClass.CONTENT)
        val links = targetsOf(map, parsed).distinct().map { dependency(symbol, placement, it) }
        return listOf(Fact.OfSymbol(symbol)) + links.map { Fact.OfRelationship(it) }
    }

    private fun targetsOf(map: Artifact, parsed: SourceMap): List<NodeRef> {
        val path = map.ref.path.value
        val resolver = SourceResolver(RepoPaths.directoryOf(path), view.packages.owning(path))
        return parsed.sources.mapNotNull { source ->
            resolver.resolve(parsed.sourceRoot, source)?.let(view.scripts::get)?.let(::nodeOf)
        }
    }

    private fun nodeOf(script: Artifact): NodeRef? {
        val placement = view.placementOf(script) ?: return null
        val kind = WebKinds.sourceFileKind(script.type)
        return NodeRef.OfSymbol(WebIdentities.fileId(placement.identity, kind, script.ref.path.value))
    }

    private fun dependency(from: Symbol, placement: Placement, target: NodeRef) = Relationship(
        from = NodeRef.OfSymbol(from.id),
        kind = RelationKind.DEPENDS_ON,
        subKind = WebKinds.SOURCE_MAP_SUB_KIND,
        resolution = Resolution.Resolved(target, Certainty.EXACT),
        evidenceClass = EvidenceClass.CONTENT,
        site = from.declaration,
        scope = placement.scope,
    )
}
