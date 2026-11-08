package io.github.krosstack.kscope.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.github.krosstack.kscope.cli.CommandGroup
import io.github.krosstack.kscope.cli.Located
import io.github.krosstack.kscope.cli.OutputFormat
import io.github.krosstack.kscope.cli.ResolvedOptions
import io.github.krosstack.kscope.cli.RootOptions
import io.github.krosstack.kscope.cli.forward
import io.github.krosstack.kscope.cli.guarded
import io.github.krosstack.kscope.cli.help.FormatGuide
import io.github.krosstack.kscope.cli.help.SharedOption
import io.github.krosstack.kscope.cli.output.echoResult
import io.github.krosstack.kscope.cli.requireIndex
import io.github.krosstack.kscope.cli.requireNoDot
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.taskInput
import io.github.krosstack.kscope.cli.text.ContextText
import io.github.krosstack.kscope.cli.textContext
import io.github.krosstack.kscope.cli.useService
import io.github.krosstack.kscope.cli.withProgress
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.service.Leveled
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ProgressListener
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.context
import io.github.krosstack.kscope.service.leveled
import io.github.krosstack.kscope.service.workspaceLocations

internal class ContextCommand : CliktCommand(name = "context") {
    private val common by RootOptions(SharedOption.QUERY, FormatGuide.CONTEXT)
    private val task by argument(
        "TASK",
        help = "What you want to do, in your own words. Leave it out when you use --task-file.",
    ).optional()
    private val taskFile by option(
        "--task-file",
        metavar = "FILE",
        help = "Read the task from a UTF-8 file instead. Use it for text with accents, CJK or emoji on Windows.",
    ).path(mustExist = true, canBeDir = false)
    private val budget by option(
        "--budget",
        metavar = "TOKENS",
        help = "The most tokens the whole context may use, task and headers included. Without it, --level decides.",
    ).int().restrictTo(min = 0)
    private val level by option(
        "--level",
        help = "How much to include: minimal, recommended, deep (adds neighbouring code and test bodies) or full.",
    ).choice(
        "minimal" to ContextLevel.MINIMAL,
        "recommended" to ContextLevel.RECOMMENDED,
        "deep" to ContextLevel.DEEP,
        "full" to ContextLevel.FULL,
    ).default(ContextLevel.RECOMMENDED, defaultForHelp = "recommended")
    private val estimator by option(
        "--estimator",
        help = "How tokens are counted: with the o200k_base or cl100k_base tokenizer, or chars (four characters " +
            "per token).",
    ).choice("o200k_base", "cl100k_base", "chars").default("o200k_base")

    override val helpTags = CommandGroup.tag(CommandGroup.ASK)

    override fun help(context: Context) = "Gathers the code worth reading for a task, within a token budget.\n\n" +
        "Use it to prepare what a coding agent should read before it starts. By default it prints a summary of " +
        "what was chosen; `--format markdown` prints the context itself, ready to paste into a prompt, and " +
        "`--format json` the fragments with their evidence. Run `kscope scan` first."

    override fun helpEpilog(context: Context) = listOf(
        "Examples:",
        "  kscope context \"add an optional reminder to tasks\" --budget 8000 --format markdown",
        "  kscope context --task-file task.txt --level deep --format json",
    ).joinToString("\n")

    override fun run() {
        val options = resolve(common)
        requireNoDot(options.format)
        val text = guarded { taskInput(task, taskFile) }
        forward(options)
        val answer = guarded {
            withProgress(options) { listener -> compile(options, text, listener) }
        }
        val located = answer.value
        val result = located.value
        echoResult(
            when (options.format) {
                OutputFormat.JSON -> ServiceJson.encode(result, answer.level)
                OutputFormat.MARKDOWN -> result.markdown
                else -> ContextText.render(result, textContext(options, located.locations))
            },
            options.format,
        )
    }

    private fun compile(
        options: ResolvedOptions,
        text: String,
        listener: ProgressListener,
    ): Leveled<Located<ContextPackage>> = useService(options, OpenOptions(progress = listener)) {
        it.requireIndex()
        val tokens = TokenEstimators.create(TokenizerId(estimator))
        val compiled = it.context(text, budget?.let(::TokenCount), level, tokens)
        it.leveled(Located(compiled, it.workspaceLocations()))
    }
}
