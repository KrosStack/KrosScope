package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.NodeLabels
import io.github.krosstack.kscope.cli.text.format.RelevanceCutoff
import io.github.krosstack.kscope.cli.text.format.Section
import io.github.krosstack.kscope.cli.text.format.Selection
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.RelatedResult

/**
 * The result of `related`: the files and symbols closest to a node in the graph. The node itself and the file that
 * declares it are always listed; the others pass the [RelevanceCutoff] measured against the best of them.
 */
internal object RelatedText {
    private const val ROWS = 10
    private const val ROOTS_SHOWN = 3
    private const val QUERY_CAP = 50
    private const val MORE_RELEVANT = "more relevant (use --detail full to list them)"

    private val BRIEF = setOf(Section.HEADLINE, Section.FILES)
    private val STANDARD = setOf(Section.HEADLINE, Section.FILES, Section.SYMBOLS, Section.SUPPORT)

    /** The sections `--sections` accepts for a related lookup. */
    val SECTIONS: List<String> = STANDARD.toList()

    fun render(result: RelatedResult, requested: String, context: TextContext): String {
        val document = TextDocument(context, context.shown { if (it == DetailLevel.BRIEF) BRIEF else STANDARD })
        if (result.roots.isEmpty()) {
            return document.conclusion("Nothing in the index is named \"$requested\".", Tone.WARNING).toString()
        }
        val rootIds = rootIds(result)
        val rootFiles = rootFiles(result, rootIds)
        val files = RelevanceCutoff.select(result.files, { it.artifact in rootFiles }) { it.score }
        val symbols = RelevanceCutoff.select(result.symbols, { it.symbol.id in rootIds }) { it.score }
        val roots = Format.listing(result.roots.map { NodeLabels.of(it, context.locations) }, ROOTS_SHOWN)
        document.conclusion(
            "${Format.noun(files.relevant.size, "file")} and ${Format.noun(symbols.relevant.size, "symbol")} " +
                "are closest to $roots; ${Format.noun(files.scored, "file")} and " +
                "${Format.noun(symbols.scored, "symbol")} scored.",
            Tone.GOOD,
        )
        files(files, context, document)
        symbols(symbols, context, document)
        footer(result, document)
        return document.toString()
    }

    private fun rootFiles(result: RelatedResult, rootIds: Set<SymbolId>): Set<ArtifactRef> {
        val declaring = result.symbols.filter { it.symbol.id in rootIds }
            .mapNotNull { it.symbol.declaration?.artifact }
        return (result.roots.filterIsInstance<NodeRef.OfArtifact>().map { it.ref } + declaring).toSet()
    }

    private fun rootIds(result: RelatedResult): Set<SymbolId> =
        result.roots.filterIsInstance<NodeRef.OfSymbol>().map { it.id }.toSet()

    private fun files(selection: Selection<RankedFile>, context: TextContext, document: TextDocument) {
        if (selection.relevant.isEmpty()) return
        val shown = selection.relevant.take(context.detail.rows(ROWS))
        val table = Table(listOf(Column("score", Align.RIGHT), Column("file", fit = Fit.PATH)))
        shown.forEach { table.row(Format.fineScore(it.score), context.path(it.artifact)) }
        document.section(heading("Closest files", shown.size, selection), Section.FILES).table(table)
            .more(selection.relevant.size - shown.size, MORE_RELEVANT)
    }

    private fun symbols(selection: Selection<RankedSymbol>, context: TextContext, document: TextDocument) {
        if (selection.relevant.isEmpty()) return
        val shown = selection.relevant.take(context.detail.rows(ROWS))
        val table = Table(listOf(Column("score", Align.RIGHT), Column("kind"), Column("symbol")))
        shown.forEach {
            table.row(Format.fineScore(it.score), it.symbol.kind.id, it.symbol.qualifiedName ?: it.symbol.name)
        }
        document.section(heading("Closest symbols", shown.size, selection), Section.SYMBOLS).table(table)
            .more(selection.relevant.size - shown.size, MORE_RELEVANT)
    }

    private fun heading(title: String, shown: Int, selection: Selection<*>): String =
        if (shown == selection.relevant.size) {
            "$title (${Format.count(shown)})"
        } else {
            "$title (showing $shown of ${Format.count(selection.relevant.size)})"
        }

    private fun footer(result: RelatedResult, document: TextDocument) {
        val cut = if (result.truncated) " The graph was cut at its limit; results can be incomplete." else ""
        val capped = if (maxOf(result.files.size, result.symbols.size) >= QUERY_CAP) {
            " The query keeps at most $QUERY_CAP files and $QUERY_CAP symbols."
        } else {
            ""
        }
        document.section(null, Section.SUPPORT)
            .note("Supported by ${Format.noun(result.relationships.size, "relationship")}.$cut$capped")
    }
}
