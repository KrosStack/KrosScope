package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.Golden
import io.github.krosstack.kscope.cli.text.format.Emphasis
import io.github.krosstack.kscope.cli.text.format.TextContext
import io.github.krosstack.kscope.service.StructureResult
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.test.Test

class StructureTextTest {
    private val context = TextContext(Path.of("workspace").toAbsolutePath(), Emphasis.NONE)

    private fun result(skipped: Int, notice: String = "null"): StructureResult = Json.decodeFromString(
        """
        {
          "directory": ".", "depth": 4,
          "conclusion": {"kind": "single-build", "summary": "A single Gradle build.", "evidence": []},
          "git": {"directory": ".", "role": "repository", "linkedWorktree": false, "evidence": ".git directory"},
          "worktrees": [], "gradleBuilds": [], "projects": [], "nestedRepositories": [],
          "directoriesVisited": 1, "truncated": false,
          "ignoredDirectoriesSkipped": $skipped, "ignoreRulesNotice": $notice
        }
        """.trimIndent(),
    )

    @Test
    fun `the structure report says how many ignored directories were skipped`() {
        Golden.assertMatches("structure-ignored", StructureText.render(result(1), context))
    }

    @Test
    fun `the structure report explains when Git ignore rules were not applied`() {
        val notice = "\"Git ignore rules were not applied: Git is unavailable.\""
        Golden.assertMatches("structure-git-unavailable", StructureText.render(result(0, notice), context))
    }
}
