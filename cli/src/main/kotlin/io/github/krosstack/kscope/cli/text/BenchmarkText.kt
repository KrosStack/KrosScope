package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.benchmark.localization.LocalizationSetResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationSuiteResult
import io.github.krosstack.kscope.cli.text.format.Align
import io.github.krosstack.kscope.cli.text.format.Column
import io.github.krosstack.kscope.cli.text.format.Format
import io.github.krosstack.kscope.cli.text.format.Table
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.cli.text.format.TextDocument
import io.github.krosstack.kscope.cli.text.format.Tone

/** The result of `benchmark` for a person: the verdict and the headline scores of each method. */
internal object BenchmarkText {
    fun render(result: LocalizationSuiteResult, context: TextContext): String {
        val document = TextDocument(context)
        document.conclusion(conclusion(result), if (result.gatePassed) Tone.GOOD else Tone.BAD)
        result.validationErrors.forEach { document.line("  Invalid: $it") }
        result.sets.forEach { set(it, document) }
        return document.hint("Use --format markdown for the full report, or --format json for the data.").toString()
    }

    private fun conclusion(result: LocalizationSuiteResult): String = when {
        !result.valid -> "The localization gate is not passed: the task corpus is invalid."
        !result.complete -> ONE_SET_ONLY
        result.gatePassed -> "The localization gate passed on both statement sets."
        else -> "The localization gate is not passed on at least one statement set."
    }

    private fun set(set: LocalizationSetResult, document: TextDocument) {
        val scoreColumns = listOf("R@5", "R@10", "NDCG@10").map { Column(it, Align.RIGHT) }
        val table = Table(listOf(Column("method")) + scoreColumns)
        set.methods.forEach { (method, scores) ->
            table.row(
                method.label,
                Format.score(scores.recall5),
                Format.score(scores.recall10),
                Format.score(scores.ndcg10),
            )
        }
        val tasks = Format.noun(set.tasks.size, "task")
        document.section("${set.kind.label}: $tasks, gate ${verdict(set.gatePassed)}").table(table)
    }

    private fun verdict(passed: Boolean): String = if (passed) "passed" else "not passed"

    private const val ONE_SET_ONLY =
        "The localization gate is not passed: only the original statements were measured; " +
            "add --held-out to measure both."
}
