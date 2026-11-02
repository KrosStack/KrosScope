package io.github.krosstack.kscope.context.compact

import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.context.fragment.DocumentRead
import io.github.krosstack.kscope.context.fragment.FragmentRenderer
import io.github.krosstack.kscope.context.fragment.SourceDocument
import io.github.krosstack.kscope.context.fragment.SourceDocuments
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextRendering

internal sealed interface RawSource {
    data class Ready(val fragment: ContextFragment) : RawSource
    data class Dropped(val omission: ContextOmission) : RawSource
}

internal class CompactSources(
    private val graph: ContextGraph,
    private val documents: SourceDocuments,
    private val renderer: FragmentRenderer,
) {
    fun of(candidate: ContextCandidate): RawSource {
        val artifact = graph.artifact(candidate.node)?.let { graph.artifacts[it] }
        return when {
            artifact == null -> dropped(candidate, ContextOmissionReason.NO_SOURCE)
            graph.generated(candidate.node) -> dropped(candidate, ContextOmissionReason.GENERATED_BODY)
            else -> read(candidate, artifact)
        }
    }

    private fun read(candidate: ContextCandidate, artifact: Artifact): RawSource =
        when (val read = documents.read(artifact)) {
            is DocumentRead.Failed -> dropped(candidate, read.reason)
            is DocumentRead.Content -> raw(candidate, artifact, read.document)
        }

    private fun raw(candidate: ContextCandidate, artifact: Artifact, document: SourceDocument): RawSource = when {
        document.text.isEmpty() -> dropped(candidate, ContextOmissionReason.NO_SOURCE)

        candidate.node is NodeRef.OfSymbol -> declaration(candidate, artifact, document)

        else -> RawSource.Ready(
            renderer.fragment(candidate, artifact, ContextRendering.FILE, document.text, document.fullRange),
        )
    }

    private fun declaration(candidate: ContextCandidate, artifact: Artifact, document: SourceDocument): RawSource {
        val symbol = (candidate.node as NodeRef.OfSymbol).let { graph.symbols[it.id] }
        val range = symbol?.declaration?.range
        val source = range?.let(document::slice)
        return if (range == null || source == null) {
            dropped(candidate, ContextOmissionReason.INVALID_RANGE)
        } else {
            val rendering = renderer.declarationRendering(artifact)
            RawSource.Ready(renderer.fragment(candidate, artifact, rendering, source, range))
        }
    }

    private fun dropped(candidate: ContextCandidate, reason: ContextOmissionReason): RawSource =
        RawSource.Dropped(ContextOmission(candidate.node, reason))
}
