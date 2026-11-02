package io.github.krosstack.kscope.cli

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedirectedOutputTest {
    private val task = "Réparer 测试 🚀"

    @Test
    fun `redirected json with non ascii text is valid utf8 on any platform charset`() = inScannedWorkspace { root ->
        val taskFile = root.resolve("task.txt")
        Files.writeString(taskFile, task, Charsets.UTF_8)

        val bytes = runWithRedirectedStreams(
            root,
            listOf("analyze", "--task-file", taskFile.toString(), "--workspace", root.toString(), "--format", "json"),
        )

        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        assertEquals(task, parseJson(decoded).getValue("query").jsonObject.getValue("text").jsonPrimitive.content)
        assertTrue(containsSequence(bytes, "é".toByteArray(Charsets.UTF_8)), "the accent is written as UTF-8")
    }

    @Test
    fun `redirected text output is valid utf8 too`() = inWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        val taskFile = root.resolve("task.txt")
        Files.writeString(taskFile, task, Charsets.UTF_8)

        val bytes = runWithRedirectedStreams(
            root,
            listOf("context", "--task-file", taskFile.toString(), "--workspace", root.toString()),
        )

        val strict = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        val decoded = strict.decode(ByteBuffer.wrap(bytes))
        assertTrue(decoded.contains(task), decoded.toString())
    }

    private fun runWithRedirectedStreams(root: Path, arguments: List<String>): ByteArray {
        val output = root.resolve("stdout.bin")
        val errors = root.resolve("stderr.txt")
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val command = listOf(
            java,
            "-Xmx256m",
            "-Dfile.encoding=windows-1252",
            "-cp",
            System.getProperty("java.class.path"),
            "io.github.krosstack.kscope.cli.MainKt",
        ) + arguments
        val process = ProcessBuilder(command)
            .redirectOutput(output.toFile())
            .redirectError(errors.toFile())
            .start()
        assertTrue(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the command did not finish")
        assertEquals(0, process.exitValue(), Files.readString(errors))
        return Files.readAllBytes(output)
    }

    private fun containsSequence(haystack: ByteArray, needle: ByteArray): Boolean =
        (0..haystack.size - needle.size).any { start -> needle.indices.all { haystack[start + it] == needle[it] } }

    private companion object {
        const val TIMEOUT_SECONDS = 120L
    }
}
