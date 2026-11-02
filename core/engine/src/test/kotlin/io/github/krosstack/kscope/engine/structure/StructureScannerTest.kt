package io.github.krosstack.kscope.engine.structure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StructureScannerTest {
    private fun scan(workspace: StructureWorkspace, depth: Int = StructureScanner.DEFAULT_DEPTH) =
        StructureScanner(depth).scan(workspace.root)

    @Test
    fun `a settings script marks a Gradle build and includeBuild names the builds it includes`() =
        StructureWorkspace().use { workspace ->
            workspace.file("settings.gradle.kts", "includeBuild(\"build-logic\")\ninclude(\":app\")\n")
            workspace.file("build-logic/settings.gradle.kts")

            val builds = scan(workspace).builds

            assertEquals(listOf(".", "build-logic"), builds.map { it.directory })
            assertEquals(listOf("build-logic"), builds.first().includedBuilds)
            assertTrue(builds.last().includedBuilds.isEmpty())
        }

    @Test
    fun `a Groovy settings script is found and its single quoted includeBuild is read`() =
        StructureWorkspace().use { workspace ->
            workspace.file("settings.gradle", "includeBuild '../shared'\n")

            val build = scan(workspace).builds.single()

            assertEquals("settings.gradle", build.settingsFile)
            assertEquals(1, build.includedBuilds.size)
            assertTrue(build.includedBuilds.single().endsWith("shared"), build.includedBuilds.toString())
        }

    @Test
    fun `other project markers are reported with their ecosystem`() = StructureWorkspace().use { workspace ->
        workspace.file("web/package.json")
        workspace.file("tool/Cargo.toml")
        workspace.file("scripts/pyproject.toml")
        workspace.file("cmd/go.mod")

        val projects = scan(workspace).projects.associate { it.directory to it.ecosystem }

        assertEquals(mapOf("web" to "node", "tool" to "cargo", "scripts" to "python", "cmd" to "go"), projects)
    }

    @Test
    fun `a git directory in the inspected directory is its repository`() = StructureWorkspace().use { workspace ->
        workspace.repository(".")

        val found = scan(workspace)

        assertEquals(CheckoutKind.REPOSITORY, assertNotNull(found.checkout).kind)
        assertEquals(".", assertNotNull(found.checkout).directory)
        assertTrue(found.repositories.isEmpty(), "the inspected directory is not a nested repository")
    }

    @Test
    fun `a linked worktree is recognised and the other worktrees of its repository are listed`() =
        StructureWorkspace().use { workspace ->
            workspace.repository("main")
            workspace.linkedWorktree("main", "feature")

            val fromWorktree = StructureScanner(0).scan(workspace.root.resolve("feature"))

            assertEquals(CheckoutKind.LINKED_WORKTREE, assertNotNull(fromWorktree.checkout).kind)
            val others = fromWorktree.worktrees.map { it.directory }
            assertEquals(listOf("../main"), others)
            assertTrue(fromWorktree.worktrees.all { it.exists })
            val fromMain = StructureScanner(0).scan(workspace.root.resolve("main"))
            assertEquals(listOf("../feature"), fromMain.worktrees.map { it.directory })
            val repository = assertNotNull(fromMain.checkout).repositoryId
            assertEquals(repository, assertNotNull(fromWorktree.checkout).repositoryId)
        }

    @Test
    fun `a worktree whose directory is gone is listed as missing`() = StructureWorkspace().use { workspace ->
        workspace.repository("main")
        workspace.linkedWorktree("main", "feature")
        workspace.root.resolve("feature").toFile().deleteRecursively()

        val others = StructureScanner(0).scan(workspace.root.resolve("main")).worktrees

        assertEquals(listOf(false), others.map { it.exists })
    }

    @Test
    fun `nested repositories are found by their git entries and a submodule is told apart`() =
        StructureWorkspace().use { workspace ->
            workspace.repository("one")
            workspace.repository("two")
            workspace.file("modules-host/sub/.git", "gitdir: ../.git/modules/sub\n")

            val kinds = scan(workspace).repositories.associate { it.directory to it.checkout.kind }

            assertEquals(
                mapOf(
                    "one" to CheckoutKind.REPOSITORY,
                    "two" to CheckoutKind.REPOSITORY,
                    "modules-host/sub" to CheckoutKind.SUBMODULE,
                ),
                kinds,
            )
        }
}
