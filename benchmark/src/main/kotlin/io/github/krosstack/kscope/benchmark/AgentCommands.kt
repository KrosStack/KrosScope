package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.agent.AgentTask
import io.github.krosstack.kscope.benchmark.agent.AgentTaskStore
import io.github.krosstack.kscope.benchmark.agent.Arm
import io.github.krosstack.kscope.benchmark.agent.RunStore
import io.github.krosstack.kscope.benchmark.agent.TaskLoad
import io.github.krosstack.kscope.benchmark.agent.agentJson
import io.github.krosstack.kscope.benchmark.agent.report.AgentReportBuilder
import io.github.krosstack.kscope.benchmark.agent.report.AgentReportMarkdown
import io.github.krosstack.kscope.benchmark.agent.report.ReportInput
import io.github.krosstack.kscope.benchmark.agent.verification.GradleVerifier
import io.github.krosstack.kscope.benchmark.agent.verification.TaskValidator
import io.github.krosstack.kscope.benchmark.agent.verification.ValidationReport
import io.github.krosstack.kscope.benchmark.agent.workspace.FileTrees
import io.github.krosstack.kscope.benchmark.agent.workspace.GitCli
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path

internal const val AGENT_USAGE_ERROR = 2
internal const val AGENT_FAILED = 1
private const val VALIDATION_REPEATS = 3

private const val VALIDATE_USAGE = "usage: agent-tasks-validate --out <directory> [--tasks <directory>] " +
    "[--workspace <directory>] [--only T01,T02] [--repeats 3] [--android-sdk <directory>]"
private const val REPORT_USAGE = "usage: agent-report --input <run directory> --out <directory> " +
    "[--tasks <directory>] [--arms A,B,C] [--repeats <n>] [--only T01,T02]"

internal fun runAgentCommand(args: List<String>, print: (String) -> Unit): Int = when (args.firstOrNull()) {
    "agent-tasks-validate" -> validateTasks(args.drop(1), print)
    "agent-run" -> runAgents(args.drop(1), print)
    "agent-report" -> reportAgents(args.drop(1), print)
    else -> AGENT_USAGE_ERROR
}

internal fun usage(text: String, print: (String) -> Unit): Int {
    print(text)
    return AGENT_USAGE_ERROR
}

internal fun loadTasks(flags: AgentFlags, print: (String) -> Unit): List<AgentTask>? =
    when (val load = AgentTaskStore.load(flags.path("--tasks", AgentDefaults.tasks()), flags.ids("--only"))) {
        is TaskLoad.Loaded -> load.tasks

        is TaskLoad.Invalid -> {
            load.problems.forEach(print)
            null
        }
    }

internal object TempWorkspaces {
    fun create(source: Path): Path {
        val temp = Files.createTempDirectory("kscope-agent-bench").toRealPath()
        val top = Path.of(GitCli().output(source, "rev-parse", "--show-toplevel").trim()).toRealPath()
        if (temp.startsWith(top)) {
            FileTrees.deleteQuietly(temp)
            error("The temporary directory must lie outside the repository tree.")
        }
        return temp
    }
}

private class ValidateOptions(val flags: AgentFlags, val repeats: Int) {
    companion object {
        private val VALUED = setOf("--out", "--tasks", "--workspace", "--only", "--repeats", "--android-sdk")

        fun parse(args: List<String>): ValidateOptions? {
            val flags = AgentFlags.parse(args, VALUED, emptySet())
            val repeats = flags?.int("--repeats", VALIDATION_REPEATS)
            return if (flags == null || flags.value("--out") == null || repeats == null) {
                null
            } else {
                ValidateOptions(flags, repeats)
            }
        }
    }
}

private fun validateTasks(args: List<String>, print: (String) -> Unit): Int {
    val options = ValidateOptions.parse(args)
    val tasks = options?.let { loadTasks(it.flags, print) }
    return when {
        options == null -> usage(VALIDATE_USAGE, print)
        tasks == null -> AGENT_FAILED
        else -> performValidation(options, tasks, print)
    }
}

private fun performValidation(options: ValidateOptions, tasks: List<AgentTask>, print: (String) -> Unit): Int {
    val flags = options.flags
    val out = flags.path("--out", ".").also { Files.createDirectories(it) }
    val source = flags.path("--workspace", AgentDefaults.workspace())
    val temp = TempWorkspaces.create(source)
    try {
        val verifier = GradleVerifier(androidSdk = flags.value("--android-sdk"))
        val validator = TaskValidator(source, temp, verifier, { out.resolve("logs").resolve(it) })
        val results = tasks.map { validator.validate(it, options.repeats, print) }
        Files.writeString(out.resolve("validation.json"), agentJson.encodeToString(results) + "\n")
        val markdown = ValidationReport.markdown(results)
        Files.writeString(out.resolve("validation.md"), markdown)
        print(markdown)
        return if (results.all { it.valid }) 0 else AGENT_FAILED
    } finally {
        FileTrees.deleteQuietly(temp)
    }
}

private class ReportOptions(val flags: AgentFlags, val arms: List<Arm>) {
    companion object {
        private val VALUED = setOf("--input", "--out", "--tasks", "--arms", "--repeats", "--only")

        fun parse(args: List<String>): ReportOptions? {
            val flags = AgentFlags.parse(args, VALUED, emptySet())
            val arms = AgentOptionParsing.arms(flags?.value("--arms"))
            val complete = flags?.takeIf { it.value("--input") != null && it.value("--out") != null }
            return complete?.let { arms?.let { selected -> ReportOptions(complete, selected) } }
        }
    }
}

private fun reportAgents(args: List<String>, print: (String) -> Unit): Int {
    val options = ReportOptions.parse(args)
    val tasks = options?.let { loadTasks(it.flags, print) }
    return when {
        options == null -> usage(REPORT_USAGE, print)
        tasks == null -> AGENT_FAILED
        else -> writeReport(options, tasks, print)
    }
}

private fun writeReport(options: ReportOptions, tasks: List<AgentTask>, print: (String) -> Unit): Int {
    val flags = options.flags
    val store = RunStore(flags.path("--input", "."))
    val binding = store.binding()
    val records = store.records()
    val repeats = flags.int("--repeats", records.maxOfOrNull { it.repeat } ?: 1)
    if (binding == null || repeats == null) {
        print("The input holds no benchmark run, or the repetition count is not valid.")
        return AGENT_FAILED
    }
    val report = AgentReportBuilder.build(ReportInput(tasks.map { it.spec }, options.arms, repeats, records, binding))
    val out = flags.path("--out", ".").also { Files.createDirectories(it) }
    Files.writeString(out.resolve("aggregate.json"), agentJson.encodeToString(report) + "\n")
    val markdown = AgentReportMarkdown.render(report)
    Files.writeString(out.resolve("report.md"), markdown)
    print(markdown)
    return 0
}
