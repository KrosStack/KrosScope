package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import com.github.ajalt.mordant.rendering.AnsiLevel
import io.github.krosstack.kscope.cli.progress.ProgressScreen
import io.github.krosstack.kscope.cli.progress.TickerFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path

internal fun writeFile(root: Path, relative: String, text: String) {
    val file = root.resolve(relative)
    Files.createDirectories(file.parent)
    Files.writeString(file, text)
}

/** A workspace with one module, one Kotlin file and one document: four artifacts, one module, two units. */
internal fun <T> inWorkspace(block: (Path) -> T): T {
    val root = Files.createTempDirectory("kscope-cli")
    try {
        writeFile(root, "settings.gradle.kts", "include(\":lib\")\n")
        writeFile(root, "lib/build.gradle.kts", "")
        writeFile(root, "lib/src/main/kotlin/Lib.kt", "class Lib\n")
        writeFile(root, "README.md", "# Sample\n")
        return block(root)
    } finally {
        root.toFile().deleteRecursively()
    }
}

/** [inWorkspace], scanned: the workspace has an index when [block] runs. */
internal fun <T> inScannedWorkspace(block: (Path) -> T): T = inWorkspace { root ->
    check(runKscope("scan", "--workspace", root.toString()).statusCode == 0) { "the sample workspace did not scan" }
    block(root)
}

internal fun runKscope(vararg arguments: String): CliktCommandTestResult {
    val command = KscopeCommand()
    return command.test(arguments.toList()) { command.parseWithExitCodes(it) }
}

/** Runs the command on a terminal of [width] columns that shows [ansiLevel], with [variables] as its environment. */
internal fun runKscopeOnTerminal(
    vararg arguments: String,
    ansiLevel: AnsiLevel = AnsiLevel.TRUECOLOR,
    variables: Map<String, String> = emptyMap(),
    width: Int = 79,
): CliktCommandTestResult {
    val command = KscopeCommand()
    return command.test(arguments.toList(), envvars = variables, ansiLevel = ansiLevel, width = width) {
        command.parseWithExitCodes(it)
    }
}

/** A clock that moves [stepMillis] every time it is read, so that a command seems to take a while. */
internal class SteppingClock(private val stepMillis: Long) {
    private var millis = 0L

    fun nanos(): Long {
        millis += stepMillis
        return millis * NANOS_PER_MILLI
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** An environment whose standard error is a string, and whose clock moves by [stepMillis] per reading. */
internal class RecordedEnvironment(stderrIsConsole: Boolean, stepMillis: Long = 1_200) {
    private val progress = StringBuilder()
    private val clock = SteppingClock(stepMillis)
    val environment = CliEnvironment(
        screen = ProgressScreen(80, stderrIsConsole) { progress.append(it) },
        clock = clock::nanos,
        tickers = TickerFactory.NONE,
        encodings = EncodingReport.DETACHED,
    )

    /** What the command wrote to standard error as progress. */
    val written: String get() = progress.toString()
}

internal fun runKscope(environment: CliEnvironment, vararg arguments: String): CliktCommandTestResult =
    runKscopeWith(environment, arguments.toList())

internal fun runKscopeWith(environment: CliEnvironment, arguments: List<String>): CliktCommandTestResult {
    val command = KscopeCommand(environment)
    return command.test(arguments) { command.parseWithExitCodes(it) }
}

internal fun runKscopeWith(arguments: List<String>): CliktCommandTestResult =
    runKscopeWith(CliEnvironment.detached(), arguments)

internal fun parseJson(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
