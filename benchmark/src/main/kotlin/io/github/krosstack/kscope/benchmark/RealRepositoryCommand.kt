package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositorySpec
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.catalog.CatalogManifestImporter
import io.github.krosstack.kscope.benchmark.realrepos.corpus.RealRepositoryCorpus
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRepositoryRecords
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRepositoryRunner
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRunOptions
import io.github.krosstack.kscope.benchmark.realrepos.report.RealRepositoryReports
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private val realRepositoryJson = Json {
    prettyPrint = true
    encodeDefaults = true
}
private const val USAGE_ERROR = 2
private const val USAGE = "usage: real-repos-prepare --repositories <json> --out <manifest> [--limit 1..60] | " +
    "real-repos-run --manifest <json> --out <directory> --split dev|test --checkpoint-key <sha256> " +
    "[--repository <id>] [--budgets 2000,4000,8000,16000] | " +
    "real-repos-report --manifest <json> --input <run-directory> --out <directory> | " +
    "real-repos-import --catalog <json> --freeze <json> --checkouts <directory> --out <manifest> [--split dev|test]"

internal fun runRealRepositoryCommand(args: List<String>, print: (String) -> Unit): Int {
    val options = parseRealRepositoryOptions(args)
    if (options == null) {
        print(USAGE)
        return USAGE_ERROR
    }
    return try {
        execute(options, print)
    } catch (failure: IOException) {
        print("Real-repository I/O failed: ${failure.message}")
        1
    } catch (failure: IllegalArgumentException) {
        print("Invalid real-repository input: ${failure.message}")
        1
    }
}

private fun execute(options: RealRepositoryOptions, print: (String) -> Unit): Int = when (options) {
    is RealRepositoryOptions.Prepare -> prepare(options, print)
    is RealRepositoryOptions.Run -> measure(options, print)
    is RealRepositoryOptions.Report -> report(options, print)
    is RealRepositoryOptions.Import -> import(options, print)
}

private fun import(options: RealRepositoryOptions.Import, print: (String) -> Unit): Int {
    val manifest = CatalogManifestImporter(options.checkouts).import(options.catalog, options.freeze, options.split)
    Files.createDirectories(options.output.toAbsolutePath().parent)
    Files.writeString(options.output, realRepositoryJson.encodeToString(manifest) + "\n")
    val nested = manifest.tasks.count { it.workspace.isNotEmpty() }
    print("Imported ${manifest.tasks.size} tasks: ${manifest.repositories.size} repositories, $nested workspace tasks")
    return 0
}

private fun prepare(options: RealRepositoryOptions.Prepare, print: (String) -> Unit): Int {
    val repositories = realRepositoryJson.decodeFromString<List<RealRepositorySpec>>(
        Files.readString(options.repositories),
    )
    val manifest = RealRepositoryCorpus.prepare(repositories, options.limit)
    Files.createDirectories(options.output.toAbsolutePath().parent)
    Files.writeString(options.output, realRepositoryJson.encodeToString(manifest) + "\n")
    val development = manifest.tasks.count { it.split == RealSplit.DEV }
    val test = manifest.tasks.count { it.split == RealSplit.TEST }
    print(
        "Prepared ${manifest.tasks.size} tasks: DEV=$development TEST=$test VERIFY=${manifest.tasks.count {
            it.verify
        }}",
    )
    return if (manifest.tasks.isEmpty()) 1 else 0
}

private fun measure(options: RealRepositoryOptions.Run, print: (String) -> Unit): Int {
    val results = RealRepositoryRunner().run(
        readManifest(options.manifest),
        RealRunOptions(options.output, options.identity, options.split, options.repository, options.budgets),
    )
    val completed = results.count { it.status == RealMeasurementStatus.COMPLETE }
    print("Measured ${options.split}: $completed complete, ${results.size - completed} failed")
    return if (results.isNotEmpty() && completed == results.size) 0 else 1
}

private fun report(options: RealRepositoryOptions.Report, print: (String) -> Unit): Int {
    val passed = RealRepositoryReports.write(
        readManifest(options.manifest),
        RealRepositoryRecords.read(options.input),
        options.output,
    )
    print("Real-repository Tier 1: ${if (passed) "PASS" else "FAIL"}")
    return if (passed) 0 else 1
}

private fun readManifest(path: Path): RealRepositoryManifest =
    realRepositoryJson.decodeFromString(Files.readString(path))
