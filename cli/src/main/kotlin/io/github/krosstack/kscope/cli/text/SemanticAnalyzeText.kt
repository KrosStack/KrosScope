package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.Section
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.ArtifactKind

internal object SemanticAnalyzeText {
    private const val ROWS = 10
    private const val MORE_RELEVANT = "more relevant (use --detail full to list them)"

    fun isApplied(status: SemanticStatus?): Boolean =
        status?.state == SemanticState.USED || status?.state == SemanticState.BEHIND

    fun status(status: SemanticStatus?): List<Pair<String, String>> = when (status?.state) {
        SemanticState.USED -> listOf("Semantic ranking" to "used")
        SemanticState.BEHIND -> listOf("Semantic ranking" to "behind ${Format.noun(status.behindChunks, "chunk")}")
        else -> emptyList()
    }

    fun append(
        result: AnalysisResult,
        context: TextContext,
        kinds: Map<ArtifactRef, ArtifactKind>,
        document: TextDocument,
    ) {
        val shown = result.files.take(context.detail.rows(ROWS))
        val table = Table(
            listOf(Column("score", Align.RIGHT), Column("role"), Column("kind"), Column("file", fit = Fit.PATH)),
        )
        shown.forEach {
            val kind = kinds[it.artifact] ?: ArtifactKind.CODE
            table.row(
                Format.score(it.score),
                it.role.name.lowercase(),
                kind.name.lowercase(),
                context.path(it.artifact),
            )
        }
        document.section("Ranked files", Section.TOP).table(table)
            .more(result.files.size - shown.size, MORE_RELEVANT)
    }
}
