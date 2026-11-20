package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.realrepos.context.runSemanticContextCommand
import io.github.krosstack.kscope.benchmark.realrepos.semantic.runSemanticCommand
import io.github.krosstack.kscope.benchmark.run.ConformanceResult
import io.github.krosstack.kscope.benchmark.run.ConformanceRun
import io.github.krosstack.kscope.benchmark.run.LevelComparison
import io.github.krosstack.kscope.benchmark.run.MarkdownReport
import io.github.krosstack.kscope.benchmark.run.ScanMode
import io.github.krosstack.kscope.benchmark.run.checkGate
import io.github.krosstack.kscope.benchmark.run.gateSelection
import io.github.krosstack.kscope.benchmark.semantic.cost.runSemanticCostCommand
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val USAGE = "usage: conformance --level syntax|resolved|exact|all (or --mode default|trusted) " +
    "--out <directory> [--workspace <directory>] [--gold <directory>]"
private const val ALL_LEVELS = "all"
private const val USAGE_ERROR = 2
private const val GATE_MISSED = 1

private val prettyJson = Json(from = KscopeJson) {
    prettyPrint = true
}

/** The options of the `conformance` command. */
internal class ConformanceOptions(val modes: List<ScanMode>, val out: Path, val workspace: Path, val gold: Path) {
    companion object {
        fun parse(args: List<String>): ConformanceOptions? {
            val flags = args.drop(1).chunked(2).associate { it.first() to it.getOrNull(1) }
            val modes = modesOf(flags["--level"] ?: flags["--mode"])
            val out = flags["--out"]?.let(Path::of)
            if (args.firstOrNull() != COMMAND || modes == null || out == null) return null
            val workspace = Path.of(flags["--workspace"] ?: System.getProperty("kscope.fixture") ?: DEFAULT_WORKSPACE)
                .toAbsolutePath().normalize()
            val gold = Path.of(flags["--gold"] ?: System.getProperty("kscope.gold") ?: DEFAULT_GOLD)
                .toAbsolutePath().normalize()
            return ConformanceOptions(modes, out, workspace, gold)
        }

        private fun modesOf(text: String?): List<ScanMode>? = when (text) {
            null -> null
            ALL_LEVELS -> ScanMode.entries.toList()
            else -> ScanMode.parse(text)?.let(::listOf)
        }

        private const val COMMAND = "conformance"
        private const val DEFAULT_WORKSPACE = "fixtures/reference-workspace"
        private const val DEFAULT_GOLD = "benchmark/reference"
    }
}

private val commands: Map<String, (List<String>, (String) -> Unit) -> Int> = mapOf(
    "localization" to ::runLocalizationCommand,
    "reclassify-localization" to ::runReclassifyLocalizationCommand,
    "context-benchmark" to ::runContextBenchmarkCommand,
    "real-repos-prepare" to ::runRealRepositoryCommand,
    "real-repos-run" to ::runRealRepositoryCommand,
    "real-repos-report" to ::runRealRepositoryCommand,
    "real-repos-import" to ::runRealRepositoryCommand,
    "real-repos-semantic" to ::runSemanticCommand,
    "real-repos-context-semantic" to ::runSemanticContextCommand,
    "semantic-cost" to ::runSemanticCostCommand,
    "schemas" to ::runSchemasCommand,
    "workspace-conformance" to ::runWorkspaceConformanceCommand,
    "workspace-tasks" to ::runWorkspaceTasksCommand,
    "agent-tasks-validate" to ::runAgentCommand,
    "agent-run" to ::runAgentCommand,
    "agent-report" to ::runAgentCommand,
)

/** Runs the command; the exit code is 0 when the gate is met, 1 when it is not and 2 for a usage error. */
internal fun runCommand(args: List<String>, print: (String) -> Unit): Int =
    (commands[args.firstOrNull()] ?: ::runConformanceCommand)(args, print)

private fun runConformanceCommand(args: List<String>, print: (String) -> Unit): Int {
    val options = ConformanceOptions.parse(args)
    if (options == null) {
        print(USAGE)
        return USAGE_ERROR
    }
    return runConformance(options, print)
}

private fun runConformance(options: ConformanceOptions, print: (String) -> Unit): Int {
    Files.createDirectories(options.out)
    val single = options.modes.singleOrNull()
    val run = ConformanceRun(options.workspace, options.gold, options.out)
    val results = options.modes.map { mode ->
        val result = run.run(mode)
        write(if (single != null) options.out else options.out.resolve(mode.label), result, print)
        result
    }
    if (single == null) writeComparison(options.out, LevelComparison(results), print)
    return if (results.all { it.checkGate(it.gateSelection()).passed }) 0 else GATE_MISSED
}

private fun write(directory: Path, result: ConformanceResult, print: (String) -> Unit) {
    Files.createDirectories(directory)
    Files.writeString(directory.resolve("metrics.json"), prettyJson.encodeToString(result) + "\n")
    val report = MarkdownReport(result).render()
    Files.writeString(directory.resolve("report.md"), report)
    print(report)
}

private fun writeComparison(out: Path, comparison: LevelComparison, print: (String) -> Unit) {
    Files.writeString(out.resolve("levels.json"), prettyJson.encodeToString(comparison.data()) + "\n")
    val report = comparison.render()
    Files.writeString(out.resolve("levels.md"), report)
    print(report)
}

public fun main(args: Array<String>) {
    exitProcess(runCommand(args.toList(), ::println))
}
