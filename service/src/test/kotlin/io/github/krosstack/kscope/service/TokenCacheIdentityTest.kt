package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `analyze` and `context` print the same whether the token counts come from the cache or are counted afresh. */
class TokenCacheIdentityTest {
    private val sample = SampleWorkspace("tests")
    private val cacheFile = sample.root.resolve(".kscope/token-counts.bin")
    private val task = "Make the Greeter greet in a different language and format the greeting"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun outputs(options: OpenOptions): List<String> = KrosScope.open(sample.root, options).use { service ->
        listOf(
            ServiceJson.encode(service.analyze(task)),
            ServiceJson.encode(service.context(task)),
            service.context(task, TokenCount(4_000)).markdown,
            service.context(task, TokenCount(12_000)).markdown,
        )
    }

    @Test
    fun `output is the same with no cache a cold cache and a warm cache`() {
        KrosScope.open(sample.root).use { it.scan() }

        val without = outputs(OpenOptions(tokenCache = false))
        assertFalse(Files.exists(cacheFile), "a run without the cache leaves no file")
        val cold = outputs(OpenOptions())
        assertTrue(Files.exists(cacheFile), "a run with the cache writes it into the index directory")
        val warm = outputs(OpenOptions())

        assertEquals(without, cold)
        assertEquals(without, warm)
    }

    @Test
    fun `deleting the cache or damaging it changes nothing but the time`() {
        KrosScope.open(sample.root).use { it.scan() }
        val reference = outputs(OpenOptions(tokenCache = false))
        outputs(OpenOptions())

        Files.delete(cacheFile)
        assertEquals(reference, outputs(OpenOptions()))

        Files.writeString(cacheFile, "damaged")
        assertEquals(reference, outputs(OpenOptions()))
        assertEquals(reference, outputs(OpenOptions()))
    }

    @Test
    fun `a scan prunes the counts of content that changed and a rebuild discards the file`() {
        KrosScope.open(sample.root).use { it.scan() }
        outputs(OpenOptions())
        val before = Files.size(cacheFile)

        sample.write(GREETER, "package sample.tests\n\nclass Greeter\n")
        KrosScope.open(sample.root).use { it.scan() }
        assertTrue(Files.size(cacheFile) < before, "the counts of the old Greeter.kt are pruned")

        KrosScope.open(sample.root).use { it.scan(rebuild = true) }
        assertFalse(Files.exists(cacheFile))
    }

    @Test
    fun `the cache is not part of what scan verify compares`() {
        KrosScope.open(sample.root).use { it.scan() }
        outputs(OpenOptions())

        val verification = KrosScope.open(sample.root).use { it.scan(verify = true) }.verification

        assertTrue(checkNotNull(verification).matches, verification.toString())
    }

    @Test
    fun `an edited file is counted again, not answered from the old entry`() {
        KrosScope.open(sample.root).use { it.scan() }
        outputs(OpenOptions())
        sample.write(GREETER, "package sample.tests\n\nclass Greeter {\n    fun hi() = 1\n}\n")
        KrosScope.open(sample.root).use { it.scan() }

        val reference = outputs(OpenOptions(tokenCache = false))

        assertEquals(reference, outputs(OpenOptions()))
    }

    private companion object {
        const val GREETER = "core/src/main/kotlin/sample/tests/Greeter.kt"
    }
}
