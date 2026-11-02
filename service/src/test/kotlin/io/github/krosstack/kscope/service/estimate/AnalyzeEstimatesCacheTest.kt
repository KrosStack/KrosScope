package io.github.krosstack.kscope.service.estimate

import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.service.context
import io.github.krosstack.kscope.service.wiring.TokenCountFile
import io.github.krosstack.kscope.service.wiring.TokenCountFileBytes
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What `analyze` asks of the token count cache, seen from the file: it recalls the measure of every fragment block and
 * writes nothing new.
 */
class AnalyzeEstimatesCacheTest {
    private val sample = SampleWorkspace("tests")
    private val cacheFile = sample.root.resolve(".kscope").resolve(TokenCountFile.FILE_NAME)
    private val task = "Make the Greeter greet in a different language and format the greeting"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun estimates() = KrosScope.open(sample.root).use { it.analyze(task).contextEstimates }

    private fun packages(): Map<ContextLevel, ContextPackage> = ContextLevel.entries.associateWith { level ->
        KrosScope.open(sample.root).use { it.context(task, level = level) }
    }

    @Test
    fun `a warm run takes the measures of file blocks from the cache and counts none of them again`() {
        KrosScope.open(sample.root).use { it.scan() }
        val cold = estimates()
        TokenCountFileBytes.write(cacheFile, TokenCountFileBytes.read(cacheFile).mapValues { it.value + POISON })

        val warm = estimates()

        val excess = ContextLevel.entries.map { warm.getValue(it).value - cold.getValue(it).value }
        assertTrue(excess.all { it >= 0 && it % POISON == 0 }, "each recalled measure counts once: $excess")
        assertTrue(excess.any { it > 0 }, "some level holds a block that is recalled: $excess")
    }

    @Test
    fun `a warm run takes the measure of every fragment block from the cache and counts none of them again`() {
        KrosScope.open(sample.root).use { it.scan() }
        val cold = estimates()
        val packages = packages()
        TokenCountFileBytes.write(cacheFile, TokenCountFileBytes.read(cacheFile).mapValues { it.value + POISON })

        val warm = estimates()

        val excess = ContextLevel.entries.associateWith { warm.getValue(it).value - cold.getValue(it).value }
        val recalled = packages.mapValues { it.value.fragments.size * POISON }
        assertTrue(packages.values.flatMap { it.fragments }.any { it.rendering in FROM_THE_GRAPH }, "$recalled")
        assertEquals(recalled, excess)
    }

    @Test
    fun `a warm run adds nothing to the cache and does not write the file`() {
        KrosScope.open(sample.root).use { it.scan() }
        val cold = estimates()
        val written = Files.readAllBytes(cacheFile)
        Files.setLastModifiedTime(cacheFile, UNTOUCHED)

        val warm = estimates()

        assertEquals(cold, warm)
        assertEquals(UNTOUCHED, Files.getLastModifiedTime(cacheFile))
        assertContentEquals(written, Files.readAllBytes(cacheFile))
    }

    private companion object {
        const val POISON = 1_000_000
        val FROM_THE_GRAPH = setOf(ContextRendering.SIGNATURE, ContextRendering.OUTLINE, ContextRendering.GENERATED)
        val UNTOUCHED: FileTime = FileTime.fromMillis(1_000_000_000_000L)
    }
}
