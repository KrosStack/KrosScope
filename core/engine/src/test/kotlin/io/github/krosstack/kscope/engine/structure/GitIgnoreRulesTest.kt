package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.discovery.ProcessResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GitIgnoreRulesTest {
    @Test
    fun `loaded rules decode UTF eight NUL paths and answer ancestor lookups without running Git`() {
        StructureWorkspace().use { workspace ->
            val commands = mutableListOf<List<String>>()
            val runner = GitIgnoreRunner { command, _, _ ->
                commands += command
                val text = if (commands.size == 1) "true\n" else "café/\u0000space name\u0000"
                ProcessResult.Completed(0, text.toByteArray(Charsets.UTF_8))
            }

            val rules = GitIgnoreRules.load(workspace.root, workspace.root, WalkBounds(4), "git", runner)

            assertIs<GitIgnoreRules.Loaded>(rules)
            assertTrue(rules.contains(workspace.root.resolve("café/child/settings.gradle.kts")))
            assertTrue(rules.contains(workspace.root.resolve("space name")))
            assertFalse(rules.contains(workspace.root.resolve("café-other")))
            assertFalse(rules.contains(workspace.root))
            assertNull(rules.notice)
            assertEquals(2, commands.size)
        }
    }

    @Test
    fun `a successful batch with no ignored paths loads empty rules`() = StructureWorkspace().use { workspace ->
        val runner = GitIgnoreRunner { command, _, _ ->
            if ("rev-parse" in command) completed(0, "true\n") else completed(1)
        }

        val rules = GitIgnoreRules.load(workspace.root, workspace.root, WalkBounds(4), "git", runner)

        assertIs<GitIgnoreRules.Loaded>(rules)
        assertFalse(rules.contains(workspace.root))
        assertNull(rules.notice)
    }

    @Test
    fun `a non work tree requires only the probe and has no fallback notice`() = StructureWorkspace().use { workspace ->
        var calls = 0
        val runner = GitIgnoreRunner { _, _, _ ->
            calls++
            completed(128)
        }

        val rules = GitIgnoreRules.load(workspace.root, null, WalkBounds(4), "git", runner)

        assertSame(GitIgnoreRules.NotWorkTree, rules)
        assertFalse(rules.contains(workspace.root))
        assertNull(rules.notice)
        assertEquals(1, calls)
    }

    @Test
    fun `a missing Git creates a permanent unavailable result`() = StructureWorkspace().use { workspace ->
        val runner = GitIgnoreRunner { _, _, _ -> ProcessResult.Incomplete("missing") }

        val rules = GitIgnoreRules.load(workspace.root, null, WalkBounds(4), "git", runner)

        assertIs<GitIgnoreRules.Failed>(rules)
        assertFalse(rules.contains(workspace.root))
        assertEquals("Git ignore rules were not applied: Git is unavailable.", rules.notice)
    }

    @Test
    fun `a failed batch keeps all directories and its notice without retrying`() {
        StructureWorkspace().use { workspace ->
            workspace.file("settings.gradle.kts")
            workspace.file("child/settings.gradle.kts")
            var calls = 0
            val runner = GitIgnoreRunner { _, _, _ ->
                calls++
                if (calls == 1) completed(0, "true\n") else completed(2)
            }

            val found = StructureScanner(4, StructureScanner.DEFAULT_MAX_DIRECTORIES, "git", runner)
                .scan(workspace.root)

            assertEquals(listOf(".", "child"), found.builds.map { it.directory })
            assertEquals(0, found.ignoredDirectoriesSkipped)
            assertEquals(
                "Git ignore rules were not applied: Git could not check ignored paths.",
                found.ignoreRulesNotice,
            )
            assertEquals(2, calls)
        }
    }

    private fun completed(exitCode: Int, text: String = ""): ProcessResult =
        ProcessResult.Completed(exitCode, text.toByteArray(Charsets.UTF_8))
}
