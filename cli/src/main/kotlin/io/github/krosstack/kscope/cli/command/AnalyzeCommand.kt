package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.forward
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.requireIndex
import io.github.krosstack.kscope.cli.requireNoDot
import io.github.krosstack.kscope.cli.requireSections
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.taskInput
import io.github.krosstack.kscope.cli.text.AnalyzeText
import io.github.krosstack.kscope.cli.text.UnitCardText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ProgressListener
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.leveled

internal class AnalyzeCommand : CliktCommand(name = "analyze") {
    private val common by RootOptions(SharedOption.PRESENTED_QUERY, FormatGuide.PLAIN_TEXT_TOO)
    private val task by argument(
        "TASK",
        help = "What you want to do in your own words, or the name of a class, function or file to describe. " +
            "Leave it out when you use --task-file.",
    ).optional()
    private val taskFile by option(
        "--task-file",
        metavar = "FILE",
        help = "Read the task from a UTF-8 file instead. Use it for text with accents, CJK or emoji on Windows.",
    ).path(mustExist = true, canBeDir = false)
    private val reading by option(
        "--as",
        help = "Choose how to read the argument: task ranks the files a task touches, symbol describes one name " +
            "or file. By default a single identifier, qualified name or existing file is read as a symbol and " +
            "anything else as a task. Text and markdown output only.",
    ).choice("task" to Reading.TASK, "symbol" to Reading.SYMBOL)

    override val helpTags = CommandGroup.tag(CommandGroup.ASK)

    override fun help(context: Context) = "Finds what a task is likely to touch, or describes a class or file.\n\n" +
        "Give it a sentence and it ranks the files by how well they fit, says which to edit and which to read, " +
        "and estimates the context size. Give it one name such as TaskRepository, or a file path, and it prints " +
        "a card instead: the declaration, what it depends on, what refers to it, its tests and the impact of a " +
        "change. The first line says which reading was used. Run `kscope scan` first.\n\n" +
        "Sections for --sections, for a task: ${AnalyzeText.SECTIONS.joinToString(", ")}. For a name or file: " +
        "${UnitCardText.SECTIONS.joinToString(", ")}."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope analyze \"store a reminder time with every task\"",
        "  kscope analyze TaskRepository --detail brief",
        "  kscope analyze --task-file task.txt --format json",
    ).joinToString("\n")

    override fun run() {
        val options = resolve(common, presentation = true)
        requireNoDot(options.format)
        val text = guarded { taskInput(task, taskFile) }
        validateSections(options, text)
        if (options.format == OutputFormat.JSON) {
            if (reading == Reading.SYMBOL) throw UsageError(JSON_NEEDS_TASK)
            forward(options)
            val result = guarded { withProgress(options) { listener -> analyse(options, text, listener) } }
            echo(ServiceJson.encode(result.value, result.level))
            return
        }
        forward(options)
        val outcome = guarded { withProgress(options) { listener -> route(options, text, listener) } }
        val sections = when (outcome) {
            is AnalyzeOutcome.Task -> AnalyzeText.SECTIONS
            is AnalyzeOutcome.Card, AnalyzeOutcome.NoUnit -> UnitCardText.SECTIONS
        }
        requireSections(options, sections)
        echo(outcome.render(text, textContext(options, outcome.locations)))
    }

    private fun analyse(options: ResolvedOptions, text: String, listener: ProgressListener) =
        useService(options, OpenOptions(progress = listener)) {
            it.requireIndex()
            it.leveled(it.analyze(text))
        }

    private fun route(options: ResolvedOptions, text: String, listener: ProgressListener): AnalyzeOutcome =
        useService(options, OpenOptions(progress = listener)) {
            it.requireIndex()
            it.analyzeAs(text, IntentRouting.route(text, reading, options.workspace))
        }

    private fun validateSections(options: ResolvedOptions, text: String) {
        if (options.sections == null) return
        val routing = IntentRouting.route(text, reading, options.workspace)
        val known = guarded { useService(options) { scope -> AnalyzeSections.known(routing, scope) } }
        requireSections(options, known)
    }

    private companion object {
        const val JSON_NEEDS_TASK =
            "--as symbol prints a card, which is text. For data about a name use `kscope symbol NAME --format json` " +
                "or `kscope impact NAME --format json`."
    }
}
