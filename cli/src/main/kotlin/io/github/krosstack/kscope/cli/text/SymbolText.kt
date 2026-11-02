package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.RelevanceCutoff
import io.github.krosstack.kscope.cli.text.format.Section
import io.github.krosstack.kscope.cli.text.format.Selection
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.SymbolResult

/** The result of `symbol`: the declarations that match a name, the best match first, cut at the relevance cut-off. */
internal object SymbolText {
    private const val ROWS = 15
    private const val QUERY_WIDTH = 60
    private const val SIGNATURE_WIDTH = 100
    private const val MORE_RELEVANT = "more relevant (use --detail full to list them)"

    private val BRIEF = setOf(Section.HEADLINE, Section.MATCHES)
    private val STANDARD = setOf(Section.HEADLINE, Section.BEST, Section.MATCHES)

    /** The sections `--sections` accepts for a symbol lookup. */
    val SECTIONS: List<String> = STANDARD.toList()

    fun render(result: SymbolResult, requested: String, context: TextContext): String {
        val document = TextDocument(context, context.shown { if (it == DetailLevel.BRIEF) BRIEF else STANDARD })
        val query = Format.clip(requested, QUERY_WIDTH)
        if (result.symbols.isEmpty()) {
            return document.conclusion("No declaration matches \"$query\".", Tone.WARNING).toString()
        }
        val selection = RelevanceCutoff.select(result.symbols, { isExact(it, requested) }) { it.score }
        val best = result.symbols.first().symbol
        val matches = Format.noun(selection.relevant.size, "relevant match", "relevant matches")
        document.conclusion(
            "$matches for \"$query\"; ${Format.noun(selection.scored, "declaration")} scored; best: ${name(best)}.",
        )
        document.section(null, Section.BEST).fields(bestMatch(best, context))
        matches(selection, context, document)
        return document.toString()
    }

    private fun isExact(ranked: RankedSymbol, requested: String): Boolean = ranked.symbol.let {
        it.id.value == requested || it.qualifiedName == requested || it.name == requested
    }

    private fun matches(selection: Selection<RankedSymbol>, context: TextContext, document: TextDocument) {
        val shown = selection.relevant.take(context.detail.rows(ROWS))
        val table = Table(
            listOf(Column("score", Align.RIGHT), Column("kind"), Column("symbol"), Column("where", fit = Fit.PATH)),
        )
        shown.forEach { table.add(it, context) }
        val heading = if (shown.size == selection.relevant.size) {
            "Matches (${Format.count(shown.size)})"
        } else {
            "Matches (showing ${shown.size} of ${Format.count(selection.relevant.size)})"
        }
        document.section(heading, Section.MATCHES).table(table)
            .more(selection.relevant.size - shown.size, MORE_RELEVANT)
        if (selection.belowCutoff > 0) {
            document.note(
                "  ${Format.noun(selection.belowCutoff, "more declaration")} scored below " +
                    "${RelevanceCutoff.percent()} of the best score (--format json lists all).",
            )
        }
    }

    private fun Table.add(ranked: RankedSymbol, context: TextContext) = row(
        Format.score(ranked.score),
        ranked.symbol.kind.id,
        name(ranked.symbol),
        location(ranked.symbol, context),
    )

    private fun bestMatch(symbol: Symbol, context: TextContext): List<Pair<String, String>> = listOfNotNull(
        "Kind" to symbol.kind.id,
        "Declared at" to location(symbol, context),
        symbol.signature?.let { "Signature" to Format.clip(it, SIGNATURE_WIDTH) },
        symbol.documentation?.takeIf { it.isNotBlank() }?.let { "Summary" to Format.clip(it, SIGNATURE_WIDTH) },
    )

    private fun name(symbol: Symbol): String = symbol.qualifiedName ?: symbol.name

    private fun location(symbol: Symbol, context: TextContext): String =
        symbol.declaration?.let { "${context.path(it.artifact)}:${it.range.startLine}" } ?: "no source"
}
