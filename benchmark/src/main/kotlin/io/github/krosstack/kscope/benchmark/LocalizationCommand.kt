package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.localization.LocalizationReport
import io.github.krosstack.kscope.benchmark.run.ScanMode
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files
import java.nio.file.Path

private const val USAGE = "usage: localization --mode default --out <directory> " +
    "[--workspace <directory>] [--gold <directory>] [--held-out <file-or-directory> | --original-only]"
private const val USAGE_ERROR = 2

internal class LocalizationOptions(val conformance: ConformanceOptions, val heldOut: Path?) {
    companion object {
        fun parse(args: List<String>): LocalizationOptions? = LocalizationArguments.parse(args)?.let { arguments ->
            val flags = arguments.flags.flatMap { (flag, value) -> listOf(flag, value) }
            val options = ConformanceOptions.parse(listOf("conformance") + flags)
            options?.takeIf { it.modes == listOf(ScanMode.DEFAULT) }?.let {
                LocalizationOptions(it, arguments.heldOut(it.gold))
            }
        }
    }
}

private class LocalizationArguments(val flags: Map<String, String>, val originalOnly: Boolean) {
    fun heldOut(gold: Path): Path? = if (originalOnly) {
        null
    } else {
        flags["--held-out"]?.let(Path::of) ?: gold.resolve("heldout/statements.json")
    }

    private fun valid(args: List<String>, pairs: List<List<String>>): Boolean =
        args.firstOrNull() == "localization" && args.count { it == "--original-only" } <= 1 &&
            pairs.all { it.size == 2 } && flags.size == pairs.size && flags.keys.all { it in FLAGS } &&
            !(originalOnly && "--held-out" in flags)

    companion object {
        private val FLAGS = setOf("--mode", "--out", "--workspace", "--gold", "--held-out")

        fun parse(args: List<String>): LocalizationArguments? {
            val pairs = args.drop(1).filterNot { it == "--original-only" }.chunked(2)
            val flags = pairs.filter { it.size == 2 }.associate { it[0] to it[1] }
            val arguments = LocalizationArguments(flags, "--original-only" in args)
            return arguments.takeIf { it.valid(args, pairs) }
        }
    }
}

internal fun runLocalizationCommand(args: List<String>, print: (String) -> Unit): Int {
    val options = LocalizationOptions.parse(args)
    if (options == null) {
        print(USAGE)
        return USAGE_ERROR
    }
    return runLocalization(options, print)
}

private fun runLocalization(options: LocalizationOptions, print: (String) -> Unit): Int {
    val paths = options.conformance
    Files.createDirectories(paths.out)
    val index = paths.out.resolve("localization-index")
    val tasks = LocalizationBenchmark.loadTasks(paths.gold.resolve("tasks"))
    val heldOut = options.heldOut?.let(LocalizationBenchmark::loadStatements)
    val result = KrosScope.open(paths.workspace, measuredOpenOptions(index)).use { scope ->
        val scan = scope.scan()
        check(scan.diagnostics.errors == 0) { "Scan failed: ${scan.diagnostics.errors} errors" }
        LocalizationBenchmark.run(scope, paths.workspace, tasks, heldOut)
    }
    val report = LocalizationReport.markdown(result)
    Files.writeString(paths.out.resolve("localization.json"), BenchmarkJson.encode(result) + "\n")
    Files.writeString(paths.out.resolve("localization.md"), report)
    print(report)
    return if (result.gatePassed) 0 else 1
}
