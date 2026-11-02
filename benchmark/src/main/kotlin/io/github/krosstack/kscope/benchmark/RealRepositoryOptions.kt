package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.model.context.TokenCount
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.Locale

internal sealed interface RealRepositoryOptions {
    data class Prepare(val repositories: Path, val output: Path, val limit: Int) : RealRepositoryOptions
    data class Run(
        val manifest: Path,
        val output: Path,
        val split: RealSplit,
        val identity: String,
        val repository: String? = null,
        val budgets: List<TokenCount> = ContextBenchmarkBudgets.values,
    ) : RealRepositoryOptions
    data class Import(
        val catalog: Path,
        val freeze: Path,
        val checkouts: Path,
        val output: Path,
        val split: RealSplit,
    ) : RealRepositoryOptions
    data class Report(val manifest: Path, val input: Path, val output: Path) : RealRepositoryOptions
}

internal fun parseRealRepositoryOptions(args: List<String>): RealRepositoryOptions? = try {
    RealRepositoryOptionParser.parse(args)
} catch (_: InvalidPathException) {
    null
}

private object RealRepositoryOptionParser {
    private const val MAXIMUM_TASKS = 60
    private val identityPattern = Regex("[a-fA-F0-9]{64}")
    private val prepareFlags = setOf("--repositories", "--out", "--limit")
    private val runFlags = setOf("--manifest", "--out", "--split", "--checkpoint-key")
    private val optionalRunFlags = setOf("--repository", "--budgets")
    private val repositoryPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")
    private val importFlags = setOf("--catalog", "--freeze", "--checkouts", "--out")
    private val reportFlags = setOf("--manifest", "--input", "--out")
    private val splits = mapOf("dev" to RealSplit.DEV, "test" to RealSplit.TEST)

    fun parse(args: List<String>): RealRepositoryOptions? = flags(args.drop(1))?.let { flags ->
        when (args.firstOrNull()) {
            "real-repos-prepare" -> prepare(flags)
            "real-repos-run" -> run(flags)
            "real-repos-report" -> report(flags)
            "real-repos-import" -> import(flags)
            else -> null
        }
    }

    private fun flags(options: List<String>): Map<String, String>? {
        val pairs = options.chunked(2)
        val flags = pairs.associate { it.first() to it.last() }
        val valid = options.size % 2 == 0 && pairs.none(::malformed) && flags.size == pairs.size
        return flags.takeIf { valid }
    }

    private fun malformed(pair: List<String>): Boolean =
        !pair.first().startsWith("--") || pair.last().isBlank() || pair.last().startsWith("--")

    private fun prepare(flags: Map<String, String>): RealRepositoryOptions.Prepare? {
        val repositories = flags["--repositories"]
        val output = flags["--out"]
        val limit = if ("--limit" in flags) flags["--limit"]?.toIntOrNull() else MAXIMUM_TASKS
        val valid = prepareFlags.containsAll(flags.keys) && limit != null && limit in 1..MAXIMUM_TASKS
        return if (valid && repositories != null && output != null) {
            RealRepositoryOptions.Prepare(Path.of(repositories), Path.of(output), checkNotNull(limit))
        } else {
            null
        }
    }

    private fun run(flags: Map<String, String>): RealRepositoryOptions.Run? {
        val split = splits[flags["--split"]]
        val identity = flags["--checkpoint-key"]
        val repository = flags["--repository"]
        val budgets = flags["--budgets"]?.let(ContextBenchmarkBudgets::parse)
        val valid = runFlagsValid(flags) && split != null && identity != null && identityPattern.matches(identity) &&
            (repository == null || repositoryPattern.matches(repository)) && ("--budgets" !in flags || budgets != null)
        return if (valid) {
            RealRepositoryOptions.Run(
                Path.of(flags.getValue("--manifest")),
                Path.of(flags.getValue("--out")),
                checkNotNull(split),
                checkNotNull(identity).lowercase(Locale.ROOT),
                repository,
                budgets ?: ContextBenchmarkBudgets.values,
            )
        } else {
            null
        }
    }

    private fun runFlagsValid(flags: Map<String, String>): Boolean =
        flags.keys.containsAll(runFlags) && optionalRunFlags.containsAll(flags.keys - runFlags)

    private fun import(flags: Map<String, String>): RealRepositoryOptions.Import? {
        val split = if ("--split" in flags) splits[flags["--split"]] else RealSplit.TEST
        val valid = flags.keys.containsAll(importFlags) && (importFlags + "--split").containsAll(flags.keys)
        return if (valid && split != null) {
            RealRepositoryOptions.Import(
                Path.of(flags.getValue("--catalog")),
                Path.of(flags.getValue("--freeze")),
                Path.of(flags.getValue("--checkouts")),
                Path.of(flags.getValue("--out")),
                split,
            )
        } else {
            null
        }
    }

    private fun report(flags: Map<String, String>): RealRepositoryOptions.Report? = if (flags.keys == reportFlags) {
        RealRepositoryOptions.Report(
            Path.of(flags.getValue("--manifest")),
            Path.of(flags.getValue("--input")),
            Path.of(flags.getValue("--out")),
        )
    } else {
        null
    }
}
