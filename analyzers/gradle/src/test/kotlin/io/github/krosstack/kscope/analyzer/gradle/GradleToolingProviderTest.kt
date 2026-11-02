package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.analyzer.gradle.tooling.FetchOutcome
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GradleToolingProviderTest {
    private val workspace = ProviderWorkspace()

    @AfterTest
    fun cleanUp() {
        workspace.close()
    }

    @Test
    fun `a workspace that is not trusted is left alone`() {
        workspace.givenSimpleBuild()
        assertEquals(BuildModelResult.NotApplicable, workspace.provide(trusted = false))
        assertTrue(workspace.fetches.isEmpty())
    }

    @Test
    fun `a workspace without a settings script has no Gradle build to ask`() {
        workspace.write("app/build.gradle.kts", "")
        assertEquals(BuildModelResult.NotApplicable, workspace.provide())
        assertTrue(workspace.fetches.isEmpty())
    }

    @Test
    fun `the fetched documents become the build model`() {
        workspace.givenSimpleBuild()
        val provided = assertIs<BuildModelResult.Provided>(workspace.provide())
        assertEquals(listOf("tasktide//:app"), provided.model.modules.map { it.id.value })
        assertEquals(listOf("tasktide//"), provided.model.buildRoots.map { it.id.value })
        assertEquals(listOf("tasktide//:app@main"), provided.model.sourceSets.map { it.id.value })
        assertEquals(listOf("/libs/stdlib.jar"), provided.classpaths.values.single())
    }

    @Test
    fun `directories the gradle model does not expose are added to the source sets`() {
        workspace.givenSimpleBuild()
        workspace.write("app/src/main/sqldelight/App.sq", "SELECT 1;")
        val provided = assertIs<BuildModelResult.Provided>(workspace.provide())
        val roots = provided.model.sourceSets.single().roots.map { it.directory.value to it.kind.value }
        assertEquals(listOf("app/src/main/kotlin" to "kotlin", "app/src/main/sqldelight" to "sqldelight"), roots)
    }

    @Test
    fun `a rejected fetch fails with the message Gradle gave`() {
        workspace.givenSimpleBuild()
        workspace.outcome = { FetchOutcome.Rejected("A problem occurred evaluating project ':app'.") }
        val failed = assertIs<BuildModelResult.Failed>(workspace.provide())
        assertEquals("gradle.tooling.failed", failed.reason.value)
        assertEquals("A problem occurred evaluating project ':app'.", failed.message)
    }

    @Test
    fun `output in another schema is an invalid model and not a crash`() {
        workspace.givenSimpleBuild()
        workspace.outcome = { FetchOutcome.Fetched(documentsOf(workspace.root, schema = 2)) }
        val failed = assertIs<BuildModelResult.Failed>(workspace.provide())
        assertEquals("gradle.tooling.invalid-model", failed.reason.value)
    }

    @Test
    fun `output that is not a model is an invalid model and not a crash`() {
        workspace.givenSimpleBuild()
        workspace.outcome = { FetchOutcome.Fetched("not json") }
        val failed = assertIs<BuildModelResult.Failed>(workspace.provide())
        assertEquals("gradle.tooling.invalid-model", failed.reason.value)
    }

    @Test
    fun `facts read from files come from the companion provider and not from Gradle`() {
        val note = Fact.OfDiagnostic(Diagnostic(DiagnosticSeverity.INFO, ReasonCode("test.fact"), "from files", null))
        val companion = FakeFactsProvider(BuildModelResult.Provided(emptyModel(), emptyList(), facts = listOf(note)))
        ProviderWorkspace(companion).use { withFacts ->
            withFacts.givenSimpleBuild()
            val provided = assertIs<BuildModelResult.Provided>(withFacts.provide())
            assertEquals(listOf<Fact>(note), provided.facts)
            assertEquals(listOf("tasktide//:app"), provided.model.modules.map { it.id.value })
        }
    }

    @Test
    fun `a companion that fails is reported and the model is still provided`() {
        val failure = BuildModelResult.Failed(ReasonCode("gradle.static.io-failed"), "catalog unreadable")
        ProviderWorkspace(FakeFactsProvider(failure)).use { withFailure ->
            withFailure.givenSimpleBuild()
            val provided = assertIs<BuildModelResult.Provided>(withFailure.provide())
            val warning = provided.diagnostics.single { it.code.value == "gradle.tooling.file-facts-unavailable" }
            assertEquals(DiagnosticSeverity.WARNING, warning.severity)
            assertTrue("catalog unreadable" in warning.message)
            assertTrue(provided.facts.isEmpty())
        }
    }

    private fun emptyModel() = BuildModel(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
}

private class FakeFactsProvider(private val result: BuildModelResult) : BuildModelProvider {
    override val producer = ProducerRef("test.facts", "1")
    override val requiresTrust = false

    override fun provide(request: BuildModelRequest): BuildModelResult = result
}
