package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.Section
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone

/** The text of `analyze` for a name or a file: a card with the declaration, its links, its tests and its impact. */
internal object UnitCardText {
    private const val ROWS = 10
    private const val MORE = "more (use --detail full to list them)"

    private val BRIEF = setOf(Section.HEADLINE, Section.DECLARATION, Section.TESTS, Section.NEXT)

    /** The sections `--sections` accepts for a unit card, in the order they are printed. */
    val SECTIONS: List<String> = listOf(
        Section.HEADLINE,
        Section.DECLARATION,
        Section.DEPENDS_ON,
        Section.REFERRED_BY,
        Section.TESTS,
        Section.IMPACT,
        Section.ALTERNATIVES,
        Section.NEXT,
    )

    fun missing(requested: String, context: TextContext, reading: String): String = document(context)
        .note(reading)
        .conclusion("Nothing in the index is named \"${Format.clip(requested, QUERY_WIDTH)}\".", Tone.WARNING)
        .hint("Run `kscope scan` if the code changed.")
        .toString()

    fun render(card: UnitCard, requested: String, context: TextContext, reading: String): String {
        val document = document(context)
        document.note(reading)
        document.conclusion(headline(card, requested), if (card.alternatives.isEmpty()) Tone.GOOD else Tone.WARNING)
        document.section(null, Section.DECLARATION).fields(declaration(card))
        links("Depends on", Section.DEPENDS_ON, card.dependsOn, context, document)
        links("Referred to by", Section.REFERRED_BY, card.referredBy, context, document)
        tests(card, context, document)
        document.section("Impact", Section.IMPACT).line("  ${card.impact}")
        alternatives(card, context, document)
        val pick = card.pick
        return document.section(null, Section.NEXT).note(
            "Next: kscope impact $pick lists everything a change affects, nearest first; " +
                "kscope related $pick shows the closest files.",
        ).toString()
    }

    private fun document(context: TextContext) =
        TextDocument(context, context.shown { if (it == DetailLevel.BRIEF) BRIEF else SECTIONS.toSet() })

    private fun headline(card: UnitCard, requested: String): String {
        if (card.alternatives.isNotEmpty()) {
            val count = card.alternatives.size + 1
            return "\"${Format.clip(requested, QUERY_WIDTH)}\" names $count declarations; this card shows " +
                "${card.subject}, the others are listed below."
        }
        val places = card.referredBy.map { it.node }.distinct().size
        val refer = if (places == 1) "refers" else "refer"
        val cover = if (card.tests.size == 1) "covers" else "cover"
        val where = card.module?.let { " in $it" }.orEmpty()
        return "${card.subject} is ${article(card.kind)} ${card.kind}$where; " +
            "${Format.noun(places, "place")} $refer to it and ${Format.noun(card.tests.size, "test")} $cover it."
    }

    private fun article(kind: String): String = if (kind.first() in "aeiou") "an" else "a"

    private fun declaration(card: UnitCard): List<Pair<String, String>> = listOfNotNull(
        "Kind" to card.kind,
        "Declared at" to card.location,
        card.signature?.let { "Signature" to it },
        card.declares?.let { "Declares" to it },
        card.module?.let { "Module" to it },
        card.sourceSet?.let { "Source set" to it },
    )

    private fun links(title: String, name: String, links: List<UnitLink>, context: TextContext, d: TextDocument) {
        if (links.isEmpty()) return
        val shown = links.take(context.detail.rows(ROWS))
        val table = Table(listOf(Column("relation"), Column("node", fit = Fit.PATH)))
        shown.forEach { table.row(it.relation, it.node) }
        d.section("$title (${Format.count(links.size)})", name).table(table).more(links.size - shown.size, MORE)
    }

    private fun tests(card: UnitCard, context: TextContext, document: TextDocument) {
        if (card.tests.isEmpty()) return
        if (context.detail == DetailLevel.BRIEF && context.sections == null) {
            document.section(null, Section.TESTS).line("  Tests to run: ${Format.count(card.tests.size)}")
            return
        }
        val shown = card.tests.take(context.detail.rows(ROWS))
        document.section("Tests to run (${Format.count(card.tests.size)})", Section.TESTS)
            .items(shown, shown.size).more(card.tests.size - shown.size, MORE)
    }

    private fun alternatives(card: UnitCard, context: TextContext, document: TextDocument) {
        if (card.alternatives.isEmpty()) return
        val shown = card.alternatives.take(context.detail.rows(ROWS))
        val table = Table(listOf(Column("symbol"), Column("kind"), Column("where", fit = Fit.PATH)))
        shown.forEach { table.row(it.symbol, it.kind, it.where) }
        document.section("Other declarations with this name (${card.alternatives.size})", Section.ALTERNATIVES)
            .table(table).more(card.alternatives.size - shown.size, MORE)
            .note("Pass one of these locations to choose it: kscope analyze <where>.")
    }

    private const val QUERY_WIDTH = 60
}
