package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.context.TokenCount
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class MultiRepositoryContextTest {
    private fun repository(root: Path, name: String, source: String) {
        val files = mapOf(
            "settings.gradle.kts" to "include(\":lib\")\n",
            "lib/build.gradle.kts" to "",
            "lib/src/main/kotlin/$name.kt" to source,
        )
        val directory = root.resolve(name)
        files.forEach { (path, text) ->
            Files.createDirectories(directory.resolve(path).parent)
            Files.writeString(directory.resolve(path), text)
        }
        TestGit.run(directory, listOf("init", "-q"))
        TestGit.run(directory, listOf("add", "-A"))
        TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
    }

    @Test
    fun `context on a root of two repositories returns source from both`() {
        assumeTrue(TestGit.available())
        val root = Files.createTempDirectory("kscope-multi")
        try {
            repository(root, "Greeter", "class Greeter {\n    fun greetPerson() = \"hello\"\n}\n")
            repository(root, "Ledger", "class Ledger {\n    fun recordEntry() = 1\n}\n")
            KrosScope.open(root).use { service ->
                service.scan()
                val markdown = service.context("change Greeter greetPerson and Ledger recordEntry", TokenCount(8_000))
                    .markdown
                assertTrue("fun greetPerson()" in markdown, markdown)
                assertTrue("fun recordEntry()" in markdown, markdown)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
