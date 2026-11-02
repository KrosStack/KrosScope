package io.github.krosstack.kscope.analyzer.gradle.statics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PUBLICATIONS = "maven.publications"

class PublicationStaticTest {
    private val tree = StaticWorkspace("publication")
    private val provided = tree.provided()

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun attributesOf(path: String) =
        provided.model.modules.single { it.path == path }.attributes.filterKeys { it.startsWith("maven.") }

    @Test
    fun `a literal group and version in the script give one entry named after the module`() {
        assertEquals(mapOf(PUBLICATIONS to "org.example.script:script:1.0.0"), attributesOf(":script"))
    }

    @Test
    fun `the group and version come from gradle properties when no script sets them`() {
        assertEquals(mapOf(PUBLICATIONS to "org.example.props:props:3.1.0"), attributesOf(":props"))
    }

    @Test
    fun `explicit publications are separate entries that take missing parts from the module`() {
        val expected = "org.example.explicit:explicit-release:2.0.0,org.example.other:explicit:2.1.0"
        assertEquals(mapOf(PUBLICATIONS to expected), attributesOf(":explicit"))
    }

    @Test
    fun `the coordinates call of the maven publish plugin is read`() {
        assertEquals(mapOf(PUBLICATIONS to "org.example.van:van-core:4.0.0"), attributesOf(":vanniktech"))
    }

    @Test
    fun `a computed version is recorded as unspecified and heuristic with one diagnostic`() {
        assertEquals(
            mapOf("maven.heuristic" to "true", PUBLICATIONS to "org.example.computed:computed:unspecified"),
            attributesOf(":computed"),
        )
        val problems = provided.problems("build.publication.not-analysed")
        assertEquals(1, problems.size)
        assertTrue("version" in problems.single().message)
        assertTrue("group and artifact only" in problems.single().message)
    }

    @Test
    fun `a module without a publishing plugin has no attributes`() {
        assertEquals(emptyMap(), attributesOf(":plain"))
    }
}
