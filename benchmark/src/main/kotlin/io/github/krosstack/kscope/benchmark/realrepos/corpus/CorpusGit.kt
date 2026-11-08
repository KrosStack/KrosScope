package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.benchmark.git.RepositorySelection
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal data class CorpusGitResult(val status: Int, val bytes: ByteArray)

internal class CorpusGit(private val checkout: Path, private val environment: Map<String, String> = System.getenv()) {
    fun read(vararg arguments: String): String = readBytes(*arguments).decodeToString(throwOnInvalidSequence = true)

    fun readBytes(vararg arguments: String): ByteArray {
        val result = run(arguments.toList())
        if (result.status != 0) {
            throw IOException("Git corpus read failed (${result.status}): ${arguments.joinToString(" ")}")
        }
        return result.bytes
    }

    fun readFields(vararg arguments: String): List<ByteArray> {
        val bytes = readBytes(*arguments)
        val fields = mutableListOf<ByteArray>()
        var start = 0
        bytes.indices.forEach { end ->
            if (bytes[end] == 0.toByte()) {
                fields.add(bytes.copyOfRange(start, end))
                start = end + 1
            }
        }
        check(start == bytes.size) { "Unterminated Git corpus record" }
        return fields
    }

    fun contains(commit: String): Boolean = run(listOf("cat-file", "-e", "$commit^{commit}")).status == 0

    fun descends(commit: String, ancestor: String): Boolean =
        run(listOf("merge-base", "--is-ancestor", ancestor, commit)).status == 0

    private fun run(arguments: List<String>): CorpusGitResult {
        val output = Files.createTempFile("kscope-corpus-git", null)
        try {
            val command = listOf(
                "git", "--no-optional-locks", "-c", "core.quotePath=false", "-c", "i18n.logOutputEncoding=UTF-8",
                "-c", "log.showSignature=false", "-c", "color.ui=false",
            ) + arguments
            val builder = ProcessBuilder(command).directory(checkout.toFile()).redirectErrorStream(true)
                .redirectOutput(output.toFile())
            RepositorySelection.applyTo(builder, environment)
            val process = builder.start()
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("Git corpus read timed out")
            }
            return CorpusGitResult(process.exitValue(), Files.readAllBytes(output))
        } catch (failure: IOException) {
            throw IOException("Git corpus ${arguments.joinToString(" ")}: ${failure.message}", failure)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
