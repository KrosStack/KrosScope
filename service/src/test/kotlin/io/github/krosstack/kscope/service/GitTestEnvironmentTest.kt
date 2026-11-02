package io.github.krosstack.kscope.service

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitTestEnvironmentTest {
    @Test
    fun `git inherits isolated configuration and test identities from the test JVM`(@TempDir directory: Path) {
        val configuration = TestGit.run(directory, listOf("config", "--show-scope", "--show-origin", "--list"))
        assertEquals(
            setOf("command"),
            configuration.lineSequence().map { it.substringBefore('\t') }.toSet(),
            configuration,
        )
        listOf("GIT_AUTHOR_IDENT", "GIT_COMMITTER_IDENT").forEach { variable ->
            val identity = TestGit.run(directory, listOf("var", variable))
            assertTrue(identity.startsWith("Test <test@example.invalid> "), identity)
        }
    }
}
