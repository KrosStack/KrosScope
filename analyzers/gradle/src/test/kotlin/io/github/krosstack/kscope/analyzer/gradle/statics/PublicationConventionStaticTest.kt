package io.github.krosstack.kscope.analyzer.gradle.statics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicationConventionStaticTest {
    private val tree = StaticWorkspace("publication-convention")
    private val provided = tree.provided()

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `a convention plugin class that sets group and version gives heuristic coordinates`() {
        val lib = provided.model.modules.single { it.path == ":lib" }
        val attributes = lib.attributes.filterKeys { it.startsWith("maven.") }
        assertEquals(
            mapOf("maven.heuristic" to "true", "maven.publications" to "org.example.library:lib:1.2.0"),
            attributes,
        )
    }
}
