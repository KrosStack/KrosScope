package io.github.krosstack.kscope.engine.scope

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanPatternTest {
    private fun matches(pattern: String, path: String): Boolean = ScanPattern(pattern).matches(path)

    @Test
    fun `patterns that follow the grammar have no problem`() {
        listOf("app", "app/**", "**/gen", "**/build/generated/**", "a/*/c", "src/?ain", "*.kt", "a/b/c", "[id]/x", "**")
            .forEach { assertNull(ScanPattern.problemWith(it), it) }
    }

    @Test
    fun `patterns outside the grammar are refused with a reason, including the forms of Windows paths`() {
        val refused = mapOf(
            "" to "must not be empty",
            "/app" to "must not start with /",
            "./app" to "must not start with ./",
            "app/" to "must not end with /",
            "a//b" to "must not hold an empty segment",
            "app\\src" to "must use / and not \\",
            "C:/app" to "must not hold :",
            "C:\\app\\src" to "must use / and not \\",
            "\\\\server\\share" to "must use / and not \\",
            "../app" to "must not hold a .. segment",
            "app/../x" to "must not hold a .. segment",
            "a/./b" to "must not hold a . segment",
            "a**" to "** must be a whole segment",
            "a/**b/c" to "** must be a whole segment",
            "a/***" to "** must be a whole segment",
            "x".repeat(ScanPattern.MAXIMUM_LENGTH + 1) to "must have at most ${ScanPattern.MAXIMUM_LENGTH} characters",
        )
        refused.forEach { (pattern, reason) -> assertEquals(reason, ScanPattern.problemWith(pattern), pattern) }
    }

    @Test
    fun `a star and a question mark stay inside one segment`() {
        assertTrue(matches("a/*.kt", "a/Main.kt"))
        assertFalse(matches("a/*.kt", "a/b/Main.kt"))
        assertTrue(matches("a/?.kt", "a/b.kt"))
        assertFalse(matches("a/?.kt", "a/bc.kt"))
        assertTrue(matches("*", "readme"))
        assertTrue(matches("a*b*c", "aXXbYYc"))
        assertFalse(matches("a*b*c", "aXXbYY"))
        assertTrue(matches("a*", "a"))
    }

    @Test
    fun `a double star segment matches zero or more segments`() {
        assertTrue(matches("**/gen", "gen"))
        assertTrue(matches("**/gen", "a/b/gen"))
        assertTrue(matches("a/**/z", "a/z"))
        assertTrue(matches("a/**/z", "a/b/c/z"))
        assertFalse(matches("a/**/z", "a/b/c"))
        assertTrue(matches("**", "any/path/at/all.kt"))
        assertTrue(matches("a/**/b/**/c", "a/x/b/y/b/c"))
    }

    @Test
    fun `a pattern also matches everything below a matching directory`() {
        assertTrue(matches("app", "app/src/Main.kt"))
        assertTrue(matches("app/**", "app/src/Main.kt"))
        assertTrue(matches("**/legacy", "a/legacy/deep/File.kt"))
        assertFalse(matches("app", "application/Main.kt"))
        assertFalse(matches("app/src", "app/Main.kt"))
    }

    @Test
    fun `matching is case sensitive and takes brackets and dots literally`() {
        assertFalse(matches("App", "app/Main.kt"))
        assertFalse(matches("app/*.KT", "app/Main.kt"))
        assertTrue(matches("[id]/x", "[id]/x"))
        assertFalse(matches("[id]/x", "i/x"))
        assertTrue(matches("*.kt", ".hidden.kt"))
        assertFalse(matches("a.b", "aXb"))
    }

    @Test
    fun `a path with a backslash or a drive letter never matches a pattern of the grammar`() {
        assertFalse(matches("app/**", "app\\Main.kt"))
        assertFalse(matches("app/*", "C:/app/Main.kt"))
    }

    @Test
    fun `matching many segments takes time proportional to the product of the lengths`() {
        val pattern = (1..40).joinToString("/") { "**/a*a*a*a*b" }
        val path = (1..400).joinToString("/") { "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
        assertFalse(matches(pattern, path))
        assertNotNull(ScanPattern(pattern))
    }
}
