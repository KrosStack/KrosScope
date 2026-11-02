package io.github.krosstack.kscope.service.structure

import io.github.krosstack.kscope.service.BuildRole
import io.github.krosstack.kscope.service.CheckoutRole
import io.github.krosstack.kscope.service.DEFAULT_STRUCTURE_DEPTH
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.StructureKind
import io.github.krosstack.kscope.service.describeStructure
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructureReportTest {
    private fun describe(root: Path, depth: Int = DEFAULT_STRUCTURE_DEPTH) = KrosScope.describeStructure(root, depth)

    @Test
    fun `one Gradle build and its included build is a single build`() = inStructureDirectory { root ->
        root.writeText("settings.gradle.kts", "includeBuild(\"build-logic\")\n")
        root.writeText("build-logic/settings.gradle.kts")

        val result = describe(root)

        assertEquals(StructureKind.SINGLE_BUILD, result.conclusion.kind)
        assertEquals(listOf(BuildRole.TOP_LEVEL, BuildRole.INCLUDED), result.gradleBuilds.map { it.role })
        assertEquals(".", result.gradleBuilds.last().includedBy)
        assertTrue(result.conclusion.evidence.single().contains("settings.gradle.kts"))
    }

    @Test
    fun `a build inside a build that does not include it is nested and independent`() = inStructureDirectory { root ->
        root.writeText("settings.gradle.kts")
        root.writeText("tools/settings.gradle.kts")
        root.makeRepository(".")

        val result = describe(root)

        assertEquals(BuildRole.NESTED_INDEPENDENT, result.gradleBuilds.single { it.directory == "tools" }.role)
        assertEquals(StructureKind.SEVERAL_BUILDS, result.conclusion.kind)
        assertTrue(result.conclusion.summary.startsWith("A repository with several builds or projects"))
        assertTrue(result.conclusion.summary.contains("2 Gradle builds"), result.conclusion.summary)
    }

    @Test
    fun `a Gradle build next to a Node project is a repository with several projects`() = inStructureDirectory { root ->
        root.writeText("settings.gradle.kts")
        root.writeText("web/package.json")

        val result = describe(root)

        assertEquals(StructureKind.SEVERAL_BUILDS, result.conclusion.kind)
        assertTrue(result.conclusion.summary.contains("1 node project"), result.conclusion.summary)
        assertEquals("web", result.projects.single().directory)
    }

    @Test
    fun `a directory of two repositories holds several repositories`() = inStructureDirectory { root ->
        root.makeRepository("first")
        root.makeRepository("second")
        root.writeText("first/settings.gradle.kts")

        val result = describe(root)

        assertEquals(StructureKind.SEVERAL_REPOSITORIES, result.conclusion.kind)
        val summary = result.conclusion.summary
        assertTrue(summary.startsWith("A directory holding 2 repositories"), summary)
        assertEquals(listOf("first", "second"), result.nestedRepositories.map { it.directory })
    }

    @Test
    fun `worktrees of one repository are counted once`() = inStructureDirectory { root ->
        root.makeRepository("main")
        root.makeLinkedWorktree("main", "feature")
        root.writeText("main/settings.gradle.kts")

        val result = describe(root)

        assertEquals(StructureKind.SINGLE_BUILD, result.conclusion.kind)
        val nested = result.nestedRepositories.associateBy { it.directory }
        assertEquals(setOf("feature", "main"), nested.keys)
        assertEquals(CheckoutRole.LINKED_WORKTREE, nested.getValue("feature").role)
        assertEquals("main", nested.getValue("feature").sameRepositoryAs)
        assertEquals(null, nested.getValue("main").sameRepositoryAs)
    }

    @Test
    fun `a checkout that is a linked worktree says so and lists the other worktrees`() = inStructureDirectory { root ->
        root.makeRepository("main")
        root.makeLinkedWorktree("main", "feature")
        root.writeText("feature/settings.gradle.kts")

        val result = describe(root.resolve("feature"))

        val git = checkNotNull(result.git)
        assertTrue(git.linkedWorktree)
        assertEquals(CheckoutRole.LINKED_WORKTREE, git.role)
        assertEquals(listOf("../main"), result.worktrees.map { it.directory })
        assertEquals(StructureKind.SINGLE_BUILD, result.conclusion.kind)
    }

    @Test
    fun `a directory with no marker has no conclusion to draw`() = inStructureDirectory { root ->
        root.writeText("notes.txt", "text")

        val result = describe(root)

        assertEquals(StructureKind.NONE, result.conclusion.kind)
        assertFalse(result.truncated)
    }

    @Test
    fun `asking for the structure creates and changes nothing`() = inStructureDirectory { root ->
        root.writeText("settings.gradle.kts")
        root.writeText("lib/build.gradle.kts")
        val before = Files.walk(root).use { paths -> paths.map { root.relativize(it).toString() }.sorted().toList() }

        describe(root)

        val after = Files.walk(root).use { paths -> paths.map { root.relativize(it).toString() }.sorted().toList() }
        assertEquals(before, after)
        assertFalse(Files.exists(root.resolve(".kscope")))
    }
}
