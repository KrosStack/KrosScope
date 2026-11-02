package io.github.krosstack.kscope.analyzer.gradle.tooling

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelCacheTest {
    private val directory = Files.createTempDirectory("kscope-model-cache")
    private val cache = ModelCache(directory)

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a stored model is read back under its key`() {
        assertNull(cache.read("a"))
        cache.write("a", "[[]]")
        assertEquals("[[]]", cache.read("a"))
    }

    @Test
    fun `only the newest model is kept`() {
        cache.write("a", "first")
        cache.write("b", "second")
        assertNull(cache.read("a"))
        assertEquals("second", cache.read("b"))
    }

    @Test
    fun `a model can be overwritten under the same key`() {
        cache.write("a", "first")
        cache.write("a", "second")
        assertEquals("second", cache.read("a"))
    }
}
