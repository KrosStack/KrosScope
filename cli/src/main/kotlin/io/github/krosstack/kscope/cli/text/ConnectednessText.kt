package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.NodeLabels
import io.github.krosstack.kscope.cli.text.format.Section
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RiskFact

/** How strongly the top files of an analysis are tied to the rest of the code, from the risk facts of the result. */
internal object ConnectednessText {
    private const val FILES_SHOWN = 5

    fun append(result: AnalysisResult, listed: List<RankedFile>, context: TextContext, document: TextDocument) {
        val rank = listed.withIndex().associate { NodeRef.OfArtifact(it.value.artifact) as NodeRef to it.index }
        val byFile = result.risks.groupBy { it.node }.filterKeys { it in rank }
            .toSortedMap(compareBy { rank.getValue(it) })
        if (byFile.isEmpty()) return
        val columns = listOf(
            Column("file", fit = Fit.PATH),
            Column("fan-in", Align.RIGHT),
            Column("fan-out", Align.RIGHT),
        )
        val table = Table(columns + Column("lines", Align.RIGHT))
        byFile.entries.take(FILES_SHOWN).forEach { (node, facts) ->
            val label = NodeLabels.of(node, context.locations)
            table.row(label, fact(facts, "fan-in"), fact(facts, "fan-out"), fact(facts, "size.lines"))
        }
        document.section("Connectedness of the top files", Section.CONNECTEDNESS).table(table)
            .more(byFile.size - FILES_SHOWN)
    }

    private fun fact(facts: List<RiskFact>, name: String): String =
        facts.firstOrNull { it.name == name }?.let { Format.count(it.value.toLong()) } ?: "-"
}
