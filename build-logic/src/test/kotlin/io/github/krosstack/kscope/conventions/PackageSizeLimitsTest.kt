package io.github.krosstack.kscope.conventions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageSizeLimitsTest {
    private val limits = PackageSizeLimits(defaultLimit = 20)

    @Test
    fun `a directory at the limit passes`() {
        assertTrue(limits.violations(mapOf("src/main/kotlin/a" to 20, "src/test/kotlin/a" to 3)).isEmpty())
    }

    @Test
    fun `a directory over the limit is reported with its name and its count`() {
        assertEquals(
            listOf("src/main/kotlin/a holds 21 Kotlin files, at most 20 are allowed"),
            limits.violations(mapOf("src/main/kotlin/a" to 21, "src/main/kotlin/b" to 20)),
        )
    }

    @Test
    fun `test directories are held to the same limit`() {
        assertEquals(
            listOf("src/test/kotlin/a holds 25 Kotlin files, at most 20 are allowed"),
            limits.violations(mapOf("src/test/kotlin/a" to 25)),
        )
    }

    @Test
    fun `a configured directory has its own limit and no other directory is affected`() {
        val relaxed = PackageSizeLimits(defaultLimit = 20, directoryLimits = mapOf("src/main/kotlin/root" to 25))

        assertTrue(relaxed.violations(mapOf("src/main/kotlin/root" to 25)).isEmpty())
        assertEquals(
            listOf(
                "src/main/kotlin/other holds 21 Kotlin files, at most 20 are allowed",
                "src/main/kotlin/root holds 26 Kotlin files, at most 25 are allowed",
            ),
            relaxed.violations(mapOf("src/main/kotlin/root" to 26, "src/main/kotlin/other" to 21)),
        )
    }

    @Test
    fun `violations are listed in the order of the directory names`() {
        val directories = mapOf("src/main/kotlin/z" to 30, "src/main/kotlin/m" to 30, "src/main/kotlin/a" to 30)

        assertEquals(
            listOf("src/main/kotlin/a", "src/main/kotlin/m", "src/main/kotlin/z"),
            limits.violations(directories).map { it.substringBefore(" holds") },
        )
    }

    @Test
    fun `no directory means no violation`() {
        assertTrue(limits.violations(emptyMap()).isEmpty())
    }
}
