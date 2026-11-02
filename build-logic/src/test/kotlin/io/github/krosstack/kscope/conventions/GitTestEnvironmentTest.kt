package io.github.krosstack.kscope.conventions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class GitTestEnvironmentTest {
    @Test
    fun `ambient git configuration and identities cannot enter test processes`() {
        val source = mapOf(
            "GIT_CONFIG_GLOBAL" to "ambient-config",
            "GIT_CONFIG_SYSTEM" to "ambient-system-config",
            "GIT_CONFIG_NOSYSTEM" to "0",
            "GIT_CONFIG_PARAMETERS" to "commit.gpgsign=true",
            "GIT_AUTHOR_NAME" to "Ambient",
            "GIT_COMMITTER_EMAIL" to "ambient@example.invalid",
            "GIT_AUTHOR_DATE" to "2000-01-01T00:00:00Z",
            "GIT_COMMITTER_DATE" to "2000-01-01T00:00:00Z",
            "GIT_DIR" to "ambient-repository",
            "PATH" to "executable-search-path",
        )

        val isolated = GitTestEnvironment.isolated(source)

        assertEquals(source["PATH"], isolated["PATH"])
        assertNotEquals(source["GIT_CONFIG_GLOBAL"], isolated["GIT_CONFIG_GLOBAL"])
        assertEquals("1", isolated["GIT_CONFIG_NOSYSTEM"])
        assertEquals("Test", isolated["GIT_AUTHOR_NAME"])
        assertEquals("test@example.invalid", isolated["GIT_COMMITTER_EMAIL"])
        assertFalse("GIT_CONFIG_PARAMETERS" in isolated)
        assertFalse("GIT_CONFIG_SYSTEM" in isolated)
        assertFalse("GIT_DIR" in isolated)
        assertFalse("GIT_AUTHOR_DATE" in isolated)
        assertFalse("GIT_COMMITTER_DATE" in isolated)
    }
}
