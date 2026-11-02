package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.localization.LocalizationReport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private const val USAGE = "usage: reclassify-localization --input <frozen-localization.json> --out <new-directory>"
private const val USAGE_ERROR = 2

internal fun runReclassifyLocalizationCommand(args: List<String>, print: (String) -> Unit): Int {
    val options = ReclassificationOptions.parse(args)
    if (options == null) {
        print(USAGE)
        return USAGE_ERROR
    }
    val frozen = BenchmarkJson.decodeLocalization(Files.readString(options.input))
    val current = LocalizationBenchmark.reclassify(frozen)
    Files.createDirectories(options.out)
    val json = options.out.resolve("localization-reclassified.json")
    val report = options.out.resolve("localization-reclassified.md")
    Files.writeString(json, BenchmarkJson.encode(current) + "\n", StandardOpenOption.CREATE_NEW)
    val markdown = LocalizationReport.markdown(current)
    Files.writeString(report, markdown, StandardOpenOption.CREATE_NEW)
    print(markdown)
    return if (current.gatePassed) 0 else 1
}

private class ReclassificationOptions(val input: Path, val out: Path) {
    companion object {
        fun parse(args: List<String>): ReclassificationOptions? {
            val pairs = args.drop(1).chunked(2)
            val flags = pairs.filter { it.size == 2 }.associate { it[0] to it[1] }
            val input = flags["--input"]?.let(Path::of)
            val out = flags["--out"]?.let(Path::of)
            return if (valid(args, pairs, flags) && input != null && out != null) {
                ReclassificationOptions(input, out)
            } else {
                null
            }
        }

        private fun valid(args: List<String>, pairs: List<List<String>>, flags: Map<String, String>): Boolean =
            args.firstOrNull() == "reclassify-localization" && pairs.all { it.size == 2 } &&
                flags.size == pairs.size && flags.keys == setOf("--input", "--out")
    }
}
