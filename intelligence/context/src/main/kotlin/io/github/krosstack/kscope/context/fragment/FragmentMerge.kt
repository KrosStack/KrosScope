package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.context.selection.FragmentUtility
import io.github.krosstack.kscope.context.token.FragmentCosts
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount

internal class FragmentMerge(
    private val graph: ContextGraph,
    private val documents: SourceDocuments,
    private val costs: FragmentCosts,
) {
    private val mergedSources = mutableMapOf<MergedSourceKey, Pair<String, TokenCount>>()
    private val order = compareBy<ContextFragment> { graph.module(it.artifact) }
        .thenBy { graph.locations.pathOf(it.artifact) }
        .thenBy { it.range?.startLine ?: 0 }.thenBy { it.range?.startColumn ?: 0 }
        .thenBy { contextNodeKey(it.node) }

    fun normalize(fragments: List<ContextFragment>): List<ContextFragment> = fragments.groupBy { it.artifact }
        .flatMap { (artifact, entries) -> normalizeFile(artifact, entries) }
        .sortedWith(order)

    fun normalizeOrdered(artifact: ArtifactRef, entries: List<ContextFragment>): List<ContextFragment> =
        normalizeFile(artifact, entries).sortedWith(order)

    private fun normalizeFile(artifact: ArtifactRef, entries: List<ContextFragment>): List<ContextFragment> {
        val generated = entries.filter { it.rendering == ContextRendering.GENERATED }.distinctBy { it.node }
        val file = entries.filter { it.rendering == ContextRendering.FILE }.maxByOrNull { it.relevance }
        if (file != null) {
            val remainder = entries.filter {
                it.rendering != ContextRendering.GENERATED && it.rendering != ContextRendering.FILE
            }
            val coveredValue = normalizeFile(artifact, remainder).sumOf(FragmentUtility::value)
            return listOf(file.copy(relevance = file.relevance + coveredValue)) + generated
        }
        val ordinary = entries.filter { it.rendering != ContextRendering.GENERATED }
        val source = ordinary.filter { it.sourceBacked && it.range != null }
        val normalized = ordinary.filterNot { it.sourceBacked }.distinctBy { it.node }
        val document = graph.artifacts[artifact]?.let(documents::read) as? DocumentRead.Content
        val merged = document?.let { merge(source, it.document) } ?: source
        return normalized.filterNot { covered(it, merged) } + merged + generated
    }

    private fun covered(fragment: ContextFragment, source: List<ContextFragment>): Boolean {
        val ranges = when (val node = fragment.node) {
            is NodeRef.OfSymbol -> listOfNotNull(graph.symbols[node.id]?.declaration?.range)
            is NodeRef.OfArtifact -> graph.declarations[node.ref].orEmpty().mapNotNull { it.declaration?.range }
            is NodeRef.OfModule, is NodeRef.OfSourceSet, is NodeRef.OfExternal -> emptyList()
        }
        return ranges.isNotEmpty() && ranges.all { range -> source.any { contains(requireNotNull(it.range), range) } }
    }

    private fun contains(outer: TextRange, inner: TextRange): Boolean {
        val startsBefore = compareValuesBy(outer, inner, TextRange::startLine, TextRange::startColumn) <= 0
        val endsAfter = compareValuesBy(outer, inner, TextRange::endLine, TextRange::endColumn) >= 0
        return startsBefore && endsAfter
    }

    private fun merge(fragments: List<ContextFragment>, document: SourceDocument): List<ContextFragment> {
        val merged = mutableListOf<ContextFragment>()
        fragments.sortedWith(compareBy<ContextFragment> { it.range?.startLine }.thenBy { it.range?.startColumn })
            .forEach { fragment ->
                val previous = merged.lastOrNull()
                if (previous != null && adjacent(previous, fragment, document)) {
                    merged[merged.lastIndex] = combine(previous, fragment, document)
                } else {
                    merged.add(fragment)
                }
            }
        return merged
    }

    private fun adjacent(left: ContextFragment, right: ContextFragment, document: SourceDocument): Boolean {
        val leftRange = requireNotNull(left.range)
        val rightRange = requireNotNull(right.range)
        val first = requireNotNull(document.offsets(leftRange))
        val second = requireNotNull(document.offsets(rightRange))
        return second.first <= first.last + 1 || (
            rightRange.startLine <= leftRange.endLine + 1 &&
                (first.last + 1 until second.first).all { document.text[it].isWhitespace() }
            )
    }

    private fun combine(left: ContextFragment, right: ContextFragment, document: SourceDocument): ContextFragment {
        val first = requireNotNull(left.range)
        val second = requireNotNull(right.range)
        val end = if (compareValuesBy(second, first, TextRange::endLine, TextRange::endColumn) > 0) second else first
        val range = TextRange(first.startLine, first.startColumn, end.endLine, end.endColumn)
        val artifact = graph.artifacts.getValue(left.artifact)
        val key = MergedSourceKey(document, artifact.contentHash, range, left.rendering)
        val (text, tokens) = mergedSources.getOrPut(key) {
            val text = requireNotNull(document.slice(range))
            text to costs.count(artifact.contentHash, range, left.rendering, text)
        }
        return left.copy(
            node = NodeRef.OfArtifact(left.artifact),
            range = range,
            relevance = if (contains(first, second)) {
                maxOf(left.relevance, right.relevance)
            } else {
                left.relevance + right.relevance
            },
            text = text,
            tokens = tokens,
        )
    }
}
