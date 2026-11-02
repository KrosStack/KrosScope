package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import java.nio.file.Files
import java.nio.file.Path

internal const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

internal val REG_EXE: String = Path.of("C:\\Windows", "System32", "reg.exe").toString()

internal fun windowsEntry(directory: ServiceDirectory, runner: CommandRunner) =
    WindowsRunEntry(directory, runner, mapOf("SystemRoot" to "C:\\Windows")::get)

internal fun commandLineOf(java: String, directory: ServiceDirectory) =
    "\"${Path.of(java)}\" \"@${directory.launchFile}\""

internal fun regImport(directory: ServiceDirectory): List<String> =
    listOf(REG_EXE, "import", directory.registrationFile.toString())

internal fun regQuery(): List<String> = listOf(REG_EXE, "query", RUN_KEY, "/v", "KrosScope")

internal fun regDelete(): List<String> = listOf(REG_EXE, "delete", RUN_KEY, "/v", "KrosScope", "/f")

internal fun regQueryOutput(java: String, argumentFile: Path): String =
    "\r\n$RUN_KEY\r\n    KrosScope    REG_SZ    \"$java\" \"@$argumentFile\"\r\n\r\n"

internal fun runnerReadingImports(contents: MutableList<ByteArray>) = RecordingRunner { command ->
    if (command[1] == "import") contents += Files.readAllBytes(Path.of(command[2]))
    CommandOutcome.Finished(0, "")
}

internal fun valueOfRegistrationFile(bytes: ByteArray): String {
    val text = bytes.toString(Charsets.UTF_16LE).removePrefix("\uFEFF")
    val line = text.lines().single { it.startsWith("\"KrosScope\"=\"") }
    val escaped = line.removePrefix("\"KrosScope\"=\"").removeSuffix("\"")
    return Regex("\\\\(.)").replace(escaped) { it.groupValues[1] }
}
