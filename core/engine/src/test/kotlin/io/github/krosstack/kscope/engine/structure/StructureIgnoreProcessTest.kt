package io.github.krosstack.kscope.engine.structure

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructureIgnoreProcessTest {
    @Test
    fun `Git process count stays at two for both two hundred and four hundred directories`() {
        assumeTrue(TestGit.available())
        for (size in listOf(200, 400)) {
            StructureWorkspace().use { workspace ->
                TestGit.output(workspace.root, "init", "-q")
                workspace.file(".gitignore", "hidden/\npackage.json\n")
                workspace.file("hidden/clone/settings.gradle.kts")
                for (index in 1..size) {
                    workspace.file("d$index/settings.gradle.kts")
                    workspace.file("d$index/package.json")
                }
                val calls = mutableListOf<List<String>>()
                val processRunner = GitIgnoreProcessRunner()
                val runner = GitIgnoreRunner { command, directory, input ->
                    calls += command
                    processRunner.run(command, directory, input)
                }

                val found = StructureScanner(4, StructureScanner.DEFAULT_MAX_DIRECTORIES, "git", runner)
                    .scan(workspace.root)

                assertEquals(size, found.builds.size)
                assertEquals(size + 1, found.directoriesVisited)
                assertEquals(1, found.ignoredDirectoriesSkipped)
                assertTrue(found.projects.isEmpty())
                assertNull(found.ignoreRulesNotice)
                assertEquals(MAX_GIT_PROCESSES, calls.size)
            }
        }
    }

    @Test
    fun `tracked paths matching ignore rules are still hidden with no index`() {
        assumeTrue(TestGit.available())
        StructureWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.file("settings.gradle.kts")
            workspace.file("hidden/settings.gradle.kts")
            TestGit.output(workspace.root, "add", ".")
            workspace.file(".gitignore", "settings.gradle.kts\nhidden/\n")

            val found = StructureScanner(4).scan(workspace.root)

            assertTrue(found.builds.isEmpty())
            assertEquals(1, found.ignoredDirectoriesSkipped)
            assertEquals(1, found.directoriesVisited)
            assertNull(found.ignoreRulesNotice)
        }
    }

    private companion object {
        const val MAX_GIT_PROCESSES = 2
    }
}
