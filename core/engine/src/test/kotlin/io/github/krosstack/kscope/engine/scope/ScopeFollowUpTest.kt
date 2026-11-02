package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.workspaceState
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val GENERATED = "app/build/generated/ksp/main/kotlin"

private class IgnoredGeneratedRoot : BuildModelProvider {
    private val delegate = StructuralBuildModelProvider()
    override val producer = ProducerRef("test.ignored-generated-root", "1")
    override val requiresTrust = false

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val provided = delegate.provide(request) as? BuildModelResult.Provided ?: return BuildModelResult.NotApplicable
        val sourceSets = provided.model.sourceSets.map { sourceSet ->
            if (!sourceSet.id.value.endsWith(":app@main")) return@map sourceSet
            sourceSet.copy(roots = sourceSet.roots + SourceRoot(RepoPath(GENERATED), SourceRootKind("kotlin"), true))
        }
        return provided.copy(model = provided.model.copy(sourceSets = sourceSets))
    }
}

class ScopeFollowUpTest {
    @Test
    fun `the stamp of the watcher changes when only tests or generated change`() =
        TestWorkspace("gradle-multi").use { workspace ->
            var scope = ScanScope.ALL
            val state = workspace.engine(scope = { scope }).workspaceState()

            val plain = state.stamp()
            scope = ScanScope(tests = false)
            val withoutTests = state.stamp()
            scope = ScanScope(generated = listOf("docs"))
            val withGenerated = state.stamp()
            scope = ScanScope.ALL

            assertNotEquals(plain, withoutTests)
            assertNotEquals(plain, withGenerated)
            assertNotEquals(withoutTests, withGenerated)
            assertEquals(plain, state.stamp())
        }

    @Test
    fun `an index with no artifacts that was built with a scope is current`() = TestWorkspace().use { workspace ->
        val scope = ScanScope(include = listOf("nowhere/**"))
        workspace.engine(scope = { scope }).use { engine ->
            engine.update()
            assertTrue(engine.isIndexedWith(scope))
            assertTrue(!engine.isIndexedWith(ScanScope.ALL))
        }
    }

    @Test
    fun `a pattern that matches only a git ignored generated file gives no note`() =
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.write("$GENERATED/Gen.kt", "class Gen\n")
            workspace.write(".gitignore", "build/\n")
            val scope = ScanScope(include = listOf("app/**", "**/Gen.kt"), exclude = listOf("**/ghost.kt"))
            val registry = coreRegistry(providers = listOf(IgnoredGeneratedRoot()))
            workspace.engine(registry, scope = { scope }).use { engine ->
                val notes = engine.update().diagnostics.filter { it.code.value == "discovery.pattern-matches-nothing" }

                assertEquals(1, notes.size, notes.toString())
                assertTrue("**/ghost.kt" in notes.single().message)
            }
        }
}
