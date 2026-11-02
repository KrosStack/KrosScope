package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.DiagnosticSeverity
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PUBLICATIONS = "maven.publications"
private const val HEURISTIC = "maven.heuristic"
private const val NOT_ANALYSED = "build.publication.not-analysed"

class PublicationPropertiesStaticTest {
    private val tree = StaticWorkspace("publication-computed")
    private val provided by lazy { tree.provided() }

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun attributesOf(path: String) =
        provided.model.modules.single { it.path == path }.attributes.filterKeys { it.startsWith("maven.") }

    @Test
    fun `a template-named publication in afterEvaluate with a computed suffix gets an unspecified version`() {
        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.shared:core:unspecified"),
            attributesOf(":core"),
        )
    }

    @Test
    fun `a computed suffix is reported as information that names the link by group and artifact only`() {
        val problem = provided.problems(NOT_ANALYSED).single { ":core" in it.message }
        assertEquals(DiagnosticSeverity.INFO, problem.severity)
        assertTrue("version is computed" in problem.message)
        assertTrue("linked by group and artifact only" in problem.message)
    }

    @Test
    fun `a version read from the gradle properties of the module is exact`() {
        assertEquals(mapOf(PUBLICATIONS to "org.example.shared:literal:1.5.0"), attributesOf(":literal"))
    }

    @Test
    fun `a property is looked up in the module first and in the build root after`() {
        assertEquals(mapOf(PUBLICATIONS to "org.example.shared:shared-module:2.4.0"), attributesOf(":shared"))
    }

    @Test
    fun `a direct read takes the module value and a provider read the build root value`() {
        assertEquals(mapOf(PUBLICATIONS to "org.example.shared:direct:1.0.0"), attributesOf(":direct"))
        assertEquals(mapOf(PUBLICATIONS to "org.example.shared:provided:2.0.0"), attributesOf(":provided"))
    }

    @Test
    fun `a direct read of a property that the module script assigns is left unread`() {
        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.shared:assigned:unspecified"),
            attributesOf(":assigned"),
        )
        val problem = provided.problems(NOT_ANALYSED).single { ":assigned" in it.message }
        assertEquals(DiagnosticSeverity.INFO, problem.severity)
        assertTrue("version is computed" in problem.message)
        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.shared:builtin:unspecified"),
            attributesOf(":builtin"),
        )
    }

    @Test
    fun `an assignment in an afterEvaluate callback counts after the top of the script, in an order it can tell`() {
        assertEquals(mapOf(PUBLICATIONS to "org.example.shared:callback:2.0"), attributesOf(":callback"))
        assertEquals(mapOf(PUBLICATIONS to "org.example.shared:nested:3.0"), attributesOf(":nested"))
        assertEquals(mapOf(PUBLICATIONS to "org.example.shared:setter:2.0"), attributesOf(":setter"))
        assertEquals(mapOf(PUBLICATIONS to "org.example.shared:qualified:2.0"), attributesOf(":qualified"))
        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.shared:mixed:unspecified"),
            attributesOf(":mixed"),
        )
        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.shared:aliased:unspecified"),
            attributesOf(":aliased"),
        )
        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.shared:ordered:unspecified"),
            attributesOf(":ordered"),
        )
        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.shared:snapshot:unspecified"),
            attributesOf(":snapshot"),
        )
        assertEquals(
            mapOf(HEURISTIC to "true", PUBLICATIONS to "org.example.shared:late:unspecified"),
            attributesOf(":late"),
        )
    }

    @Test
    fun `a group that cannot be read records nothing and keeps the diagnostic`() {
        assertEquals(emptyMap(), attributesOf(":unreadable"))
        val problem = provided.problems(NOT_ANALYSED).single { ":unreadable" in it.message }
        assertEquals(DiagnosticSeverity.WARNING, problem.severity)
        assertTrue("the group is computed" in problem.message)
    }

    @Test
    fun `a property that no gradle properties file defines leaves the version computed`() {
        tree.write("literal/gradle.properties", "other=1")

        val attributes = provided.model.modules.single { it.path == ":literal" }.attributes
        assertEquals("org.example.shared:literal:unspecified", attributes[PUBLICATIONS])
        assertEquals("true", attributes[HEURISTIC])
    }
}
