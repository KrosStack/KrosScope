package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.benchmark.BenchmarkJson
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.localization.LocalizationReport
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.requireNoDot
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.text.BenchmarkText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files

internal class BenchmarkCommand : CliktCommand(name = "benchmark") {
    private val common by RootOptions(
        setOf(SharedOption.WORKSPACE, SharedOption.FORMAT),
        FormatGuide.REPORT,
    )
    private val tasks by option(
        "--tasks",
        metavar = "DIR",
        help = "A directory of task files in JSON: an id, a statement, and the files that must be edited or " +
            "read. Required.",
    ).path(mustExist = true, canBeFile = false).required()
    private val heldOut by option(
        "--held-out",
        metavar = "PATH",
        help = "A file, or a directory holding statements.json, of reworded statements to score against the " +
            "same expected files.",
    ).path(mustExist = true)
    private val originalOnly by option(
        "--original-only",
        help = "Measure only the original statements. It cannot be combined with --held-out.",
    ).flag()
    private val out by option(
        "--out",
        metavar = "DIR",
        help = "Also write localization.json and localization.md to this directory.",
    ).path(canBeFile = false)

    override val helpTags = CommandGroup.tag(CommandGroup.DIAGNOSE)

    override fun help(context: Context) = "Developer tool: scores how well kscope finds the right files.\n\n" +
        "For each task it ranks files and scores the ranking against the files that should be found, next to " +
        "plain text-search baselines. Use it to judge kscope's own file search, not your project. It reads the " +
        "existing index, so run `kscope scan` first. Exit code 1 means the check failed or the task files are " +
        "invalid."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope benchmark --tasks path/to/tasks --original-only",
        "  kscope benchmark --tasks path/to/tasks --held-out path/to/statements.json --out results",
    ).joinToString("\n")

    override fun run() {
        val options = resolve(common)
        requireNoDot(options.format)
        if (originalOnly && heldOut != null) throw UsageError("--original-only and --held-out cannot be combined.")
        val result = guarded {
            KrosScope.open(options.workspace).use { scope ->
                LocalizationBenchmark.run(
                    scope,
                    options.workspace,
                    LocalizationBenchmark.loadTasks(tasks),
                    heldOut?.let(LocalizationBenchmark::loadStatements),
                )
            }
        }
        val json = BenchmarkJson.encode(result)
        val report = LocalizationReport.markdown(result)
        guarded {
            out?.let {
                Files.createDirectories(it)
                Files.writeString(it.resolve("localization.json"), "$json\n")
                Files.writeString(it.resolve("localization.md"), report)
            }
        }
        echoResult(
            when (options.format) {
                OutputFormat.JSON -> json
                OutputFormat.MARKDOWN -> report
                else -> BenchmarkText.render(result, textContext(options))
            },
            options.format,
        )
        if (!result.gatePassed) throw ProgramResult(ExitCodes.FAILURE)
    }
}
