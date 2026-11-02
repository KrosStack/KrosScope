package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.analyzer.gradle.tooling.FetchOutcome
import io.github.krosstack.kscope.spi.BuildModelResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GradleToolingProviderCacheTest {
    private val workspace = ProviderWorkspace()

    @AfterTest
    fun cleanUp() {
        workspace.close()
    }

    @Test
    fun `an unchanged workspace is answered from the cache`() {
        workspace.givenSimpleBuild()
        val first = workspace.provide()
        val second = workspace.provide()
        assertEquals(1, workspace.fetches.size)
        assertEquals(first, second)
    }

    @Test
    fun `a changed build script fetches the model again`() {
        workspace.givenSimpleBuild()
        workspace.provide()
        workspace.write("app/build.gradle.kts", "plugins { kotlin(\"jvm\") }\ndependencies { }")
        workspace.provide()
        assertEquals(2, workspace.fetches.size)
    }

    @Test
    fun `a new source directory fetches the model again because the layout is part of the model`() {
        workspace.givenSimpleBuild()
        workspace.provide()
        workspace.write("app/src/test/kotlin/AppTest.kt", "class AppTest")
        workspace.provide()
        assertEquals(2, workspace.fetches.size)
    }

    @Test
    fun `a model cached for another checkout is fetched again because its paths lie in that checkout`() {
        workspace.givenSimpleBuild()
        workspace.provide()
        ProviderWorkspace(sharedIndex = workspace.index).use { other ->
            other.givenSimpleBuild()
            val provided = assertIs<BuildModelResult.Provided>(other.provide())
            assertEquals(listOf(other.root), other.fetches)
            assertEquals(listOf(":app"), provided.model.modules.map { it.path })
        }
    }

    @Test
    fun `a failed fetch is not cached`() {
        workspace.givenSimpleBuild()
        workspace.outcome = { FetchOutcome.Rejected("boom") }
        workspace.provide()
        workspace.outcome = { FetchOutcome.Fetched(documentsOf(workspace.root)) }
        assertIs<BuildModelResult.Provided>(workspace.provide())
        assertEquals(2, workspace.fetches.size)
    }

    @Test
    fun `an included build that the first fetch reports is not fetched on its own`() {
        workspace.givenSimpleBuild()
        workspace.write("build-logic/settings.gradle.kts", "")
        workspace.outcome = { FetchOutcome.Fetched(documentsOf(workspace.root, includedBuild = "build-logic")) }
        val provided = assertIs<BuildModelResult.Provided>(workspace.provide())
        assertEquals(listOf(workspace.root), workspace.fetches)
        assertEquals(listOf("tasktide//", "tasktide//build-logic"), provided.model.buildRoots.map { it.id.value })
    }

    @Test
    fun `a settings script that no fetched build covers is fetched as a build of its own`() {
        workspace.givenSimpleBuild()
        workspace.write("tools/settings.gradle.kts", "")
        workspace.provide()
        assertEquals(listOf(workspace.root, workspace.root.resolve("tools")), workspace.fetches)
    }
}
