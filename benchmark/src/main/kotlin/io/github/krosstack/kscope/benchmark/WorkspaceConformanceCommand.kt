package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.workspace.WorkspaceConformanceReport
import io.github.krosstack.kscope.benchmark.workspace.WorkspaceConformanceRun
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private const val USAGE =
    "usage: workspace-conformance [--out <directory>] [--workspace <directory>] [--gold <directory>]"
private const val DEFAULT_FIXTURE = "fixtures/workspace-of-repositories"
private const val DEFAULT_GOLD = "benchmark/workspace"
private const val USAGE_ERROR = 2
private const val GATE_MISSED = 1

private val prettyJson = Json(from = KscopeJson) {
    prettyPrint = true
}

/** The options of the `workspace-conformance` command: where to write the result, and the fixture and gold to read. */
internal class WorkspaceConformanceOptions(val out: Path?, val fixture: Path, val gold: Path) {
    companion object {
        private val FLAGS = setOf("--out", "--workspace", "--gold")

        fun parse(args: List<String>): WorkspaceConformanceOptions? {
            val pairs = args.drop(1).chunked(2)
            if (pairs.any { it.size != 2 || it.first() !in FLAGS }) return null
            val flags = pairs.associate { it.first() to it.last() }
            val fixture = flags["--workspace"] ?: System.getProperty("kscope.workspaceFixture") ?: DEFAULT_FIXTURE
            val gold = flags["--gold"] ?: System.getProperty("kscope.workspaceGold") ?: DEFAULT_GOLD
            return WorkspaceConformanceOptions(
                out = flags["--out"]?.let(Path::of),
                fixture = Path.of(fixture).toAbsolutePath().normalize(),
                gold = Path.of(gold).toAbsolutePath().normalize(),
            )
        }
    }
}

/** Runs the command; the exit code is 0 when every class is found with its expected evidence, 1 if not, 2 on usage. */
internal fun runWorkspaceConformanceCommand(args: List<String>, print: (String) -> Unit): Int {
    val options = WorkspaceConformanceOptions.parse(args)
    if (options == null) {
        print(USAGE)
        return USAGE_ERROR
    }
    val scratch = options.out?.let { Files.createDirectories(it) } ?: Files.createTempDirectory("kscope-workspace-run")
    try {
        val result = WorkspaceConformanceRun(options.fixture, options.gold, scratch).run()
        val report = WorkspaceConformanceReport(result).render()
        if (options.out != null) {
            Files.writeString(scratch.resolve("workspace-links.json"), prettyJson.encodeToString(result) + "\n")
            Files.writeString(scratch.resolve("workspace-links.md"), report)
        }
        print(report)
        return if (result.passed) 0 else GATE_MISSED
    } finally {
        if (options.out == null) scratch.toFile().deleteRecursively()
    }
}
