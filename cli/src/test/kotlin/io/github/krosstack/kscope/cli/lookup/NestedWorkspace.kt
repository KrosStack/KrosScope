package io.github.krosstack.kscope.cli.lookup

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.freshness.PatientScans
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.service.lookup.IndexLookup
import java.nio.file.Files
import java.nio.file.Path

internal const val LIB_FILE = "lib/src/main/kotlin/Lib.kt"
internal const val NO_INDEX_LINE = "error: No index found. Run `kscope scan` first.\n"

internal class NestedWorkspace(val base: Path) {
    val root: Path = base.resolve("workspace")
    val folder: Path = root.resolve("lib/src/main/kotlin")
    val index: String = slashed(root.resolve(".kscope"))
    val line: String = "Using the index of a parent folder: $index. The answer covers the whole workspace " +
        "${slashed(root)}, and paths are relative to it."

    fun run(arguments: List<String>): CliktCommandTestResult = runIn(folder, base, arguments)

    fun stated(arguments: List<String>): CliktCommandTestResult =
        runIn(base, base, arguments + listOf("--workspace", root.toString()))
}

internal fun slashed(path: Path): String = path.toString().replace('\\', '/')

internal fun runIn(folder: Path, stop: Path, arguments: List<String>): CliktCommandTestResult {
    val execution = CommandExecution(folder, scanRunner = PatientScans, lookup = IndexLookup(stop))
    val command = KscopeCommand(execution = execution)
    return command.test(arguments) { command.parseWithExitCodes(it) }
}

internal fun scanned(workspace: Path): Path {
    writeFile(workspace, "README.md", "# Sample\n")
    val scan = runKscope("scan", "--level", "syntax", "--quiet", "--workspace", workspace.toString())
    check(scan.statusCode == 0) { "the workspace did not scan: ${scan.output}" }
    return workspace
}

internal fun <T> inTemporaryFolder(block: (Path) -> T): T {
    val base = Files.createTempDirectory("kscope-cli-lookup").toRealPath()
    try {
        return block(base)
    } finally {
        base.toFile().deleteRecursively()
    }
}

internal fun <T> inNestedWorkspace(block: (NestedWorkspace) -> T): T = inTemporaryFolder { base ->
    val nested = NestedWorkspace(base)
    writeFile(nested.root, "settings.gradle.kts", "include(\":lib\")\n")
    writeFile(nested.root, "lib/build.gradle.kts", "")
    writeFile(nested.root, LIB_FILE, "class Lib\n")
    writeFile(nested.root, "lib/README.md", "# Library\n")
    scanned(nested.root)
    block(nested)
}
