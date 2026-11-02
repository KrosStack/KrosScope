package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.agent.AgentRunner
import io.github.krosstack.kscope.benchmark.agent.AgentTask
import io.github.krosstack.kscope.benchmark.agent.RunBinding
import io.github.krosstack.kscope.benchmark.agent.RunEnvironment
import io.github.krosstack.kscope.benchmark.agent.RunStore
import io.github.krosstack.kscope.benchmark.agent.RunSummary
import io.github.krosstack.kscope.benchmark.agent.RunnerSettings
import io.github.krosstack.kscope.benchmark.agent.SingleRun
import io.github.krosstack.kscope.benchmark.agent.launch.AgentExecutor
import io.github.krosstack.kscope.benchmark.agent.launch.NoopExecutor
import io.github.krosstack.kscope.benchmark.agent.launch.ProcessAgentExecutor
import io.github.krosstack.kscope.benchmark.agent.launch.ReferenceExecutor
import io.github.krosstack.kscope.benchmark.agent.verification.GradleVerifier
import io.github.krosstack.kscope.benchmark.agent.workspace.ContextPreparer
import io.github.krosstack.kscope.benchmark.agent.workspace.FileTrees
import io.github.krosstack.kscope.benchmark.agent.workspace.PromptTemplate
import io.github.krosstack.kscope.context.TokenEstimators
import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_BUDGET = 4000
private const val DEFAULT_RUN_MINUTES = 15L

private const val RUN_USAGE = "usage: agent-run --out <directory> (--agent claude|codex [--model <name>] | " +
    "--dry-run --stand-in noop|reference) [--tasks <directory>] [--workspace <directory>] [--only T01,T02] " +
    "[--arms A,B,C] [--repeats 1] [--budget 4000] [--timeout-minutes 15] [--global-minutes <n>] [--seed 1] " +
    "[--prompt-transport stdin|argument] [--agent-executable <file>] [--android-sdk <directory>] " +
    "[--keep-failed] [--keep-workspaces]"

private val RUN_VALUED = setOf(
    "--out", "--agent", "--model", "--stand-in", "--tasks", "--workspace", "--only", "--arms", "--repeats", "--budget",
    "--timeout-minutes", "--global-minutes", "--seed", "--prompt-transport", "--agent-executable", "--android-sdk",
)
private val RUN_SWITCHES = setOf("--dry-run", "--keep-failed", "--keep-workspaces")

private class RunOptions(
    val flags: AgentFlags,
    val budget: Int,
    val settings: RunnerSettings,
    val executor: AgentExecutor,
) {
    val out: Path get() = flags.path("--out", ".")
    val keepWorkspaces: Boolean get() = flags.has("--keep-workspaces")

    companion object {
        fun parse(args: List<String>): RunOptions? =
            AgentFlags.parse(args, RUN_VALUED, RUN_SWITCHES)?.takeIf { it.value("--out") != null }?.let(::fromFlags)

        private fun fromFlags(flags: AgentFlags): RunOptions? {
            val settings = settingsOf(flags)
            val budget = flags.int("--budget", DEFAULT_BUDGET)
            val executor = executorOf(flags)
            return if (settings != null && budget != null && executor != null) {
                RunOptions(flags, budget, settings, executor)
            } else {
                null
            }
        }

        private fun settingsOf(flags: AgentFlags): RunnerSettings? {
            val arms = AgentOptionParsing.arms(flags.value("--arms"))
            val repeats = flags.int("--repeats", 1)
            val perRun = AgentOptionParsing.minutes(flags.value("--timeout-minutes"), DEFAULT_RUN_MINUTES)
            val cap = AgentOptionParsing.minutes(flags.value("--global-minutes"), null)
            val capValid = flags.value("--global-minutes") == null || cap != null
            val seed = flags.value("--seed")?.toLongOrNull() ?: 1L
            val keepFailed = flags.has("--keep-failed")
            if (arms == null || repeats == null || perRun == null) return null
            return RunnerSettings(arms, repeats, seed, perRun, cap, keepFailed).takeIf { capValid }
        }
    }
}

private fun executorOf(flags: AgentFlags): AgentExecutor? {
    if (flags.has("--dry-run")) {
        return when (flags.value("--stand-in")) {
            "noop" -> NoopExecutor()
            "reference" -> ReferenceExecutor()
            else -> null
        }
    }
    val transport = AgentOptionParsing.transport(flags.value("--prompt-transport"))
    val adapter = transport?.let { AgentOptionParsing.adapter(flags.value("--agent"), flags.value("--model"), it) }
    return adapter?.let { ProcessAgentExecutor(it, executable = flags.value("--agent-executable")?.let(Path::of)) }
}

internal fun runAgents(args: List<String>, print: (String) -> Unit): Int {
    val options = RunOptions.parse(args)
    val tasks = options?.let { loadTasks(it.flags, print) }
    return when {
        options == null -> usage(RUN_USAGE, print)
        tasks == null -> AGENT_FAILED
        else -> bindAndRun(options, tasks, print)
    }
}

private fun bindAndRun(options: RunOptions, tasks: List<AgentTask>, print: (String) -> Unit): Int {
    val estimator = TokenEstimators.create()
    val store = RunStore(options.out.also { Files.createDirectories(it) })
    val binding = RunBinding(
        agent = options.executor.name,
        model = options.executor.model,
        budget = options.budget,
        estimator = estimator.id.value,
        promptVersion = PromptTemplate.VERSION,
        dryRun = options.executor.dryRun,
    )
    val mismatch = store.bind(binding)
    if (mismatch != null) return usage(mismatch, print)
    val source = options.flags.path("--workspace", AgentDefaults.workspace())
    val temp = TempWorkspaces.create(source)
    try {
        val environment = RunEnvironment(source, temp, store::logDirectory, options.keepWorkspaces)
        val verifier = GradleVerifier(androidSdk = options.flags.value("--android-sdk"))
        val single = SingleRun(environment, options.executor, verifier, ContextPreparer(options.budget, estimator))
        val summary = AgentRunner(options.settings, store, single, print).run(tasks, options.executor.dryRun)
        print(summaryText(summary))
        return if (summary.clean) 0 else AGENT_FAILED
    } finally {
        if (!options.keepWorkspaces) FileTrees.deleteQuietly(temp)
    }
}

private fun summaryText(summary: RunSummary): String = buildString {
    appendLine(
        "completed ${summary.completed.size}, failed ${summary.failed.size}, kept ${summary.reused.size}, " +
            "not started ${summary.notStarted.size}",
    )
    summary.failed.forEach { appendLine("failed: $it") }
    summary.notStarted.forEach { appendLine("not started: $it") }
    summary.mismatches.forEach { appendLine("mismatch: $it") }
}.trimEnd()
