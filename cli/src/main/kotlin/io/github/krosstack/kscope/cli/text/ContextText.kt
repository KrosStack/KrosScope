package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Fit
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextOmission
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextPackage

/** The result of `context` for a person: what was chosen and what it costs. The package is `--format markdown`. */
internal object ContextText {
    private const val ROWS_SHOWN = 15
    private const val TASK_WIDTH = 100

    fun render(result: ContextPackage, context: TextContext): String {
        val document = TextDocument(context)
        if (result.fragments.isEmpty()) empty(result, document) else describe(result, context, document)
        return document.toString()
    }

    private fun empty(result: ContextPackage, document: TextDocument) {
        document.conclusion("No source fits this task and budget.", Tone.WARNING).line().fields(details(result))
    }

    private fun describe(result: ContextPackage, context: TextContext, document: TextDocument) {
        document.conclusion(conclusion(result), Tone.GOOD)
        document.line().fields(details(result))
        val columns = listOf(
            Column("tokens", Align.RIGHT),
            Column("role"),
            Column("shown as"),
            Column("where", fit = Fit.PATH),
        )
        val table = Table(columns)
        result.fragments.take(ROWS_SHOWN).forEach {
            table.row(Format.count(it.tokens.value), role(it), rendering(it), where(it, context))
        }
        document.section("Fragments (${Format.count(result.fragments.size)})").table(table)
            .more(result.fragments.size - ROWS_SHOWN)
        omissions(result.omitted, document)
        document.hint(PACKAGE_HINT)
    }

    private fun conclusion(result: ContextPackage): String {
        val files = result.fragments.map { it.artifact }.distinct().size
        val tokens = Format.count(result.totalTokens.value)
        val budget = result.budget?.let { " of a budget of ${Format.count(it.value)}" }.orEmpty()
        val fragments = Format.noun(result.fragments.size, "fragment")
        return "$fragments from ${Format.noun(files, "file")}: $tokens tokens$budget."
    }

    private fun details(result: ContextPackage): List<Pair<String, String>> = listOf(
        "Task" to Format.clip(result.task, TASK_WIDTH),
        "Level" to result.level.name.lowercase(),
        "Token count by" to result.estimator.value,
    )

    private fun role(fragment: ContextFragment): String = fragment.role.name.lowercase()

    private fun rendering(fragment: ContextFragment): String = fragment.rendering.name.lowercase()

    private fun where(fragment: ContextFragment, context: TextContext): String {
        val path = context.path(fragment.artifact)
        return fragment.range?.let { "$path:${it.startLine}-${it.endLine}" } ?: path
    }

    private fun omissions(omitted: List<ContextOmission>, document: TextDocument) {
        if (omitted.isEmpty()) return
        val reasons = omitted.groupingBy { it.reason }.eachCount().entries.sortedByDescending { it.value }
        document.section("Left out (${Format.count(omitted.size)})")
            .items(reasons.map { "${Format.count(it.value)} ${reason(it.key)}" })
    }

    private fun reason(reason: ContextOmissionReason): String = when (reason) {
        ContextOmissionReason.BUDGET -> "over the budget"
        ContextOmissionReason.UNREADABLE -> "unreadable"
        ContextOmissionReason.BINARY -> "binary"
        ContextOmissionReason.NO_SOURCE -> "without source in the workspace"
        ContextOmissionReason.INVALID_RANGE -> "with a range that no longer fits the file"
        ContextOmissionReason.LEVEL_LIMIT -> "beyond the chosen level"
        ContextOmissionReason.GENERATED_BODY -> "generated code, shown as signatures"
        ContextOmissionReason.STALE_INDEX -> "changed since the last scan"
        ContextOmissionReason.OVERSIZED -> "too large"
    }

    private const val PACKAGE_HINT =
        "This is a summary. Use --format markdown for the context to paste into a prompt, " +
            "or --format json for all data."
}
