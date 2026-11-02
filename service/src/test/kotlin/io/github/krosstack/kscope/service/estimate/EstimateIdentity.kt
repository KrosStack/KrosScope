package io.github.krosstack.kscope.service.estimate

import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson
import io.github.krosstack.kscope.service.wiring.TokenCountFile
import java.nio.file.Files
import java.nio.file.Path

/** Compares `analyze` on one scanned workspace, in every state of the cache, with the oracle that compiles packages. */
internal class EstimateIdentity(private val root: Path) {
    private val cacheFile = root.resolve(".kscope").resolve(TokenCountFile.FILE_NAME)

    fun differences(tasks: List<String>): List<String> = tasks.flatMap { task ->
        val expected = compiledJson(task)
        CacheState.entries.filter { analyzedJson(it, task) != expected }.map { "$task [$it]" }
    }

    fun compiledJson(task: String): String =
        KrosScope.open(root, OpenOptions(tokenCache = false)).use { ServiceJson.encode(it.analyzeByCompiling(task)) }

    fun compiledEstimates(task: String): Map<ContextLevel, TokenCount> =
        KrosScope.open(root, OpenOptions(tokenCache = false)).use { it.analyzeByCompiling(task).contextEstimates }

    fun analyzedJson(state: CacheState, task: String): String {
        prepare(state, task)
        return KrosScope.open(root, optionsOf(state)).use { ServiceJson.encode(it.analyze(task)) }
    }

    private fun optionsOf(state: CacheState) = OpenOptions(tokenCache = state != CacheState.NONE)

    private fun prepare(state: CacheState, task: String) {
        when (state) {
            CacheState.NONE -> Unit
            CacheState.COLD -> Files.deleteIfExists(cacheFile)
            CacheState.WARM -> warmUp(task)
            CacheState.DAMAGED -> damage(task)
        }
    }

    private fun warmUp(task: String) {
        Files.deleteIfExists(cacheFile)
        KrosScope.open(root).use { it.analyze(task) }
    }

    private fun damage(task: String) {
        warmUp(task)
        val bytes = if (Files.exists(cacheFile)) Files.readAllBytes(cacheFile) else ByteArray(0)
        Files.write(cacheFile, if (bytes.size > CUT) bytes.copyOf(bytes.size - CUT) else DAMAGED.toByteArray())
    }

    private companion object {
        const val CUT = 3
        const val DAMAGED = "damaged"
    }
}
