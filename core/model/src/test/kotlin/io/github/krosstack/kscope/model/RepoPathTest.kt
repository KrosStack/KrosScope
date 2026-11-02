package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepoPathTest {
    @Test
    fun `normalized relative paths are accepted`() {
        listOf("", "a", "a/b", "src/main/kotlin/Foo.kt", "dir with space/x.kt", ".hidden/x").forEach {
            assertEquals(it, RepoPath(it).value)
        }
    }

    @Test
    fun `absolute paths are rejected`() {
        listOf("/etc/passwd", "C:/Users/x", "c:\\Users\\x", "C:relative").forEach {
            assertFailsWith<IllegalArgumentException>(it) { RepoPath(it) }
        }
    }

    @Test
    fun `paths that are not normalized are rejected`() {
        listOf("a//b", "a/", "./a", "a/./b", "../a", "a/../b", "a\\b").forEach {
            assertFailsWith<IllegalArgumentException>(it) { RepoPath(it) }
        }
    }
}
