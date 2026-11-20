package io.github.krosstack.kscope.benchmark.semantic.cost

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal class WindowsResidentMemory(private val start: (ProcessBuilder) -> Process = ProcessBuilder::start) {
    fun read(pids: List<Long>): Map<Long, Long?> = try {
        val output = capture()?.lineSequence()?.mapNotNull(::parseRow)?.toMap().orEmpty()
        pids.associateWith { output[it] }
    } catch (_: IOException) {
        pids.associateWith { null }
    } catch (_: SecurityException) {
        pids.associateWith { null }
    }

    private fun capture(): String? {
        val output = Files.createTempFile("kscope-resident-", ".csv")
        return try {
            val process = start(
                ProcessBuilder("tasklist.exe", "/FO", "CSV", "/NH")
                    .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD),
            )
            readOutput(process, output)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private fun readOutput(process: Process, output: Path): String? = try {
        if (process.waitFor(READ_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0 &&
            Files.size(output) <= MAX_OUTPUT_BYTES
        ) {
            Files.newInputStream(output).use { it.readNBytes(MAX_OUTPUT_BYTES).decodeToString() }
        } else {
            null
        }
    } finally {
        if (process.isAlive) process.destroyForcibly().waitFor()
        process.inputStream.close()
        process.outputStream.close()
        process.errorStream.close()
    }

    private fun parseRow(line: String): Pair<Long, Long?>? {
        val row = ROW.matchEntire(line.trim()) ?: return null
        val kibibytes = row.groupValues[2].filter { it in '0'..'9' }.toLongOrNull()
        return row.groupValues[1].toLongOrNull()?.let { pid ->
            pid to kibibytes?.takeIf { it in 1..Long.MAX_VALUE / KIBIBYTE }?.times(KIBIBYTE)
        }
    }

    private companion object {
        val ROW = Regex("""^"(?:[^"]|"")*","([0-9]+)","(?:[^"]|"")*","(?:[^"]|"")*","((?:[^"]|"")*)"$""")
        const val KIBIBYTE = 1024L
        const val MAX_OUTPUT_BYTES = 1_048_576
        const val READ_SECONDS = 2L
    }
}
