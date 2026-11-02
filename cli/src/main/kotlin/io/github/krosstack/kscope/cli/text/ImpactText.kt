package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.NodeLabels
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.ImpactEntry
import io.github.krosstack.kscope.retrieval.ImpactResult

/** The result of `impact`: what a change to a node can affect, nearest first. */
internal object ImpactText {
    private const val ROWS_SHOWN = 15

    fun render(result: ImpactResult, requested: String, context: TextContext): String {
        val document = TextDocument(context)
        if (result.roots.isEmpty()) {
            return document.conclusion("Nothing in the index is named \"$requested\".", Tone.WARNING).toString()
        }
        document.conclusion(summary(result), Tone.GOOD)
        val roots = result.roots.map { NodeLabels.of(it, context.locations) }
        document.line().fields(listOf("Changed" to Format.listing(roots, ROOTS_SHOWN)))
        affected(result.affected, context, document)
        modulesAndTests(result, context, document)
        return document.toString()
    }

    /** The one line that says what a change to the node reaches and how much of it tests cover. */
    fun summary(result: ImpactResult): String {
        val files = result.affected.count { it.node is NodeRef.OfArtifact }
        val modules = result.scope.modules.size
        return "A change here can affect ${Format.noun(result.affected.size, "node")} " +
            "(${Format.noun(files, "file")}, ${Format.noun(modules, "module")}) and is covered by " +
            "${Format.noun(result.tests.size, "test")}."
    }

    private fun affected(entries: List<ImpactEntry>, context: TextContext, document: TextDocument) {
        if (entries.isEmpty()) return
        val columns = listOf(Column("distance", Align.RIGHT), Column("certainty"), Column("node", fit = Fit.PATH))
        val table = Table(columns)
        entries.take(ROWS_SHOWN).forEach {
            table.row(it.distance.toString(), it.certainty, NodeLabels.of(it.node, context.locations))
        }
        document.section("Affected, nearest first").table(table).more(entries.size - ROWS_SHOWN)
    }

    private fun modulesAndTests(result: ImpactResult, context: TextContext, document: TextDocument) {
        val modules = result.scope.modules.map { NodeLabels.module(it) }
        if (modules.isNotEmpty()) document.section("Modules").items(modules)
        val tests = result.tests.map(context::path)
        if (tests.isNotEmpty()) document.section("Tests (${Format.count(tests.size)})").items(tests)
        document.line().fields(listOf("Unresolved nearby" to Format.count(result.unresolved.size)))
    }

    private const val ROOTS_SHOWN = 5
}
