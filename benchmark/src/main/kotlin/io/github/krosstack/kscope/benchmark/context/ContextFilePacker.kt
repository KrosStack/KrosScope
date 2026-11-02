package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class ContextFilePacker(
    private val documents: Map<ArtifactRef, SourceDocument>,
    private val estimator: TokenEstimator,
    private val names: WorkspaceNames = WorkspaceNames.SINGLE,
) {
    private val filesByPath = documents.values.associateBy { names.of(it.artifact) }

    fun pack(task: String, paths: List<String>, budget: TokenCount): ContextPackage {
        val selected = mutableListOf<ContextFragment>()
        for (document in paths.distinct().mapNotNull(filesByPath::get)) {
            val full = fragment(document, document.text)
            if (estimator.count(render(task, selected + full)).value <= budget.value) {
                selected.add(full)
            } else {
                prefix(task, selected, document, budget)?.let(selected::add)
                break
            }
        }
        val markdown = estimator.truncate(render(task, selected), budget)
        return ContextPackage(
            task,
            ContextLevel.FULL,
            budget,
            estimator.id,
            estimator.count(markdown),
            selected,
            markdown = markdown,
        )
    }

    fun costs(task: String, paths: List<String>, goldFiles: Set<ArtifactRef>): FullFileCoverageCosts =
        FixedOrderCoverageCosts(documents, estimator, names).measure(task, paths, goldFiles)

    private fun prefix(
        task: String,
        selected: List<ContextFragment>,
        document: SourceDocument,
        budget: TokenCount,
    ): ContextFragment? {
        val empty = fragment(document, "")
        val allowance = maxOf(0, budget.value - estimator.count(render(task, selected + empty)).value)
        var text = estimator.truncate(document.text, TokenCount(allowance))
        while (text.isNotEmpty()) {
            val candidate = fragment(document, text)
            if (estimator.count(render(task, selected + candidate)).value <= budget.value) return candidate
            text = shorter(text)
        }
        return null
    }

    private fun shorter(text: String): String {
        val next = estimator.truncate(text, TokenCount(maxOf(0, estimator.count(text).value - 1)))
        val paired = text.last().isLowSurrogate() && text.length > 1 && text[text.lastIndex - 1].isHighSurrogate()
        val points = if (paired) 2 else 1
        return if (next.length < text.length) next else text.dropLast(points)
    }

    private fun fragment(document: SourceDocument, text: String): ContextFragment {
        require(document.text.startsWith(text)) { "A baseline truncation must be an exact source prefix." }
        return ContextFragment(
            document.artifact, document.range(SourceInterval(0, text.length)), NodeRef.OfArtifact(document.artifact),
            ContextRendering.FILE, ContextRole.READ, 1.0, estimator.count(text), text, true,
        )
    }

    private fun render(task: String, fragments: List<ContextFragment>): String =
        ContextMarkdown.renderContents(task, fragments)
}
