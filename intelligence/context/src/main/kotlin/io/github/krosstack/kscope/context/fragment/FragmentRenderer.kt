package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.context.token.FragmentCosts
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole

internal data class ContextOffer(val variants: List<ContextFragment>)

internal data class RenderedContext(val offers: List<ContextOffer>, val omitted: List<ContextOmission>)

internal class FragmentRenderer(
    private val graph: ContextGraph,
    private val documents: SourceDocuments,
    private val costs: FragmentCosts,
) {
    fun render(candidate: ContextCandidate, level: ContextLevel): RenderedContext {
        val artifact = graph.artifact(candidate.node)?.let { graph.artifacts[it] }
        return when {
            artifact == null -> failed(candidate, ContextOmissionReason.NO_SOURCE)
            graph.generated(candidate.node) -> generated(candidate, artifact, level)
            else -> source(candidate, artifact, level)
        }
    }

    private fun source(candidate: ContextCandidate, artifact: Artifact, level: ContextLevel): RenderedContext =
        when (val read = documents.read(artifact)) {
            is DocumentRead.Failed -> failed(candidate, read.reason)
            is DocumentRead.Content -> ordinary(candidate, artifact, read.document, level)
        }

    private fun ordinary(
        candidate: ContextCandidate,
        artifact: Artifact,
        document: SourceDocument,
        level: ContextLevel,
    ): RenderedContext = when {
        document.text.isEmpty() -> failed(candidate, ContextOmissionReason.NO_SOURCE)

        level == ContextLevel.FULL -> RenderedContext(
            listOf(fileOffer(candidate, artifact, document, level)),
            emptyList(),
        )

        candidate.node is NodeRef.OfSymbol -> symbolOffer(candidate, artifact, document, level)

        else -> fileOffers(candidate, artifact, document, level)
    }

    private fun symbolOffer(
        candidate: ContextCandidate,
        artifact: Artifact,
        document: SourceDocument,
        level: ContextLevel,
    ): RenderedContext {
        val node = candidate.node as NodeRef.OfSymbol
        val symbol = graph.symbols.getValue(node.id)
        val signature = fragment(candidate, artifact, ContextRendering.SIGNATURE, graph.signature(symbol))
        val range = symbol.declaration?.range
        val source = range?.let(document::slice)
        val small = (candidate.role == ContextRole.TEST && level < ContextLevel.DEEP) ||
            (candidate.role == ContextRole.BOUNDARY && artifact.type.category == ArtifactCategory.SOURCE)
        val variants = if (small || source == null) {
            listOf(signature)
        } else {
            listOf(
                signature,
                fragment(candidate, artifact, declarationRendering(artifact), source, range),
            )
        }
        val omitted = if (!small && source == null) {
            listOf(ContextOmission(candidate.node, ContextOmissionReason.INVALID_RANGE))
        } else {
            emptyList()
        }
        return RenderedContext(listOf(ContextOffer(variants)), omitted)
    }

    private fun fileOffers(
        candidate: ContextCandidate,
        artifact: Artifact,
        document: SourceDocument,
        level: ContextLevel,
    ): RenderedContext {
        val symbols = graph.declarations[artifact.ref].orEmpty().filterNot { graph.generated(NodeRef.OfSymbol(it.id)) }
        val relevance = candidate.relevance / symbols.size.coerceAtLeast(1)
        val declarations = symbols.map { symbol ->
            symbolOffer(
                candidate.copy(node = NodeRef.OfSymbol(symbol.id), relevance = relevance),
                artifact,
                document,
                level,
            )
        }
        return RenderedContext(
            listOf(fileOffer(candidate, artifact, document, level)) + declarations.flatMap { it.offers },
            declarations.flatMap { it.omitted },
        )
    }

    private fun fileOffer(
        candidate: ContextCandidate,
        artifact: Artifact,
        document: SourceDocument,
        level: ContextLevel,
    ): ContextOffer {
        val source = fragment(candidate, artifact, ContextRendering.FILE, document.text, document.fullRange)
        if (level == ContextLevel.FULL) return ContextOffer(listOf(source))
        val signatures = graph.declarations[artifact.ref].orEmpty().sortedBy { it.id.value }.map(graph::signature)
        val packageLine = document.text.lineSequence().firstOrNull { it.trimStart().startsWith("package ") }
        val outlineText = (listOfNotNull(packageLine) + signatures).joinToString("\n").ifEmpty {
            "Source ${graph.locations.pathOf(artifact.ref)}"
        }
        val outline = fragment(candidate, artifact, ContextRendering.OUTLINE, outlineText)
        val variants = if (candidate.role == ContextRole.TEST && level < ContextLevel.DEEP) {
            listOf(outline)
        } else {
            listOf(outline, source)
        }
        return ContextOffer(variants)
    }

    private fun generated(candidate: ContextCandidate, artifact: Artifact, level: ContextLevel): RenderedContext {
        val symbol = (candidate.node as? NodeRef.OfSymbol)?.let { graph.symbols[it.id] }
        val signature = symbol?.let(graph::signature) ?: "Generated surface"
        val text = "$signature\nGenerated from ${graph.locations.pathOf(artifact.ref)}"
        val generated = ContextOffer(listOf(fragment(candidate, artifact, ContextRendering.GENERATED, text)))
        val source = if (level == ContextLevel.FULL) {
            render(candidate.copy(node = NodeRef.OfArtifact(artifact.ref)), level)
        } else {
            RenderedContext(emptyList(), emptyList())
        }
        return source.copy(offers = listOf(generated) + source.offers)
    }

    fun fragment(
        candidate: ContextCandidate,
        artifact: Artifact,
        rendering: ContextRendering,
        text: String,
        range: TextRange? = null,
    ): ContextFragment {
        val symbol = (candidate.node as? NodeRef.OfSymbol)?.let { graph.symbols[it.id] }
        val note = symbol?.takeIf { it.certainty != Certainty.EXACT }?.let {
            "${it.certainty.name.lowercase()} ${it.evidenceClass.name.lowercase()} evidence"
        }
        return ContextFragment(
            artifact.ref, range, candidate.node, rendering, candidate.role, candidate.relevance,
            costs.count(artifact.contentHash, range, rendering, text), text, range != null, note,
        )
    }

    fun declarationRendering(artifact: Artifact): ContextRendering = when (artifact.type.category) {
        ArtifactCategory.SOURCE, ArtifactCategory.NATIVE -> ContextRendering.DECLARATION

        ArtifactCategory.BUILD, ArtifactCategory.RESOURCE, ArtifactCategory.SCHEMA, ArtifactCategory.CONFIG,
        ArtifactCategory.DOCUMENTATION, ArtifactCategory.OTHER,
        -> ContextRendering.ENTRY
    }

    private fun failed(candidate: ContextCandidate, reason: ContextOmissionReason): RenderedContext =
        RenderedContext(emptyList(), listOf(ContextOmission(candidate.node, reason)))
}
