package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.MavenPublicationDocument
import io.github.krosstack.kscope.analyzer.gradle.ModelDocuments
import io.github.krosstack.kscope.analyzer.gradle.PublicationDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicationMappingTest {
    @Test
    fun `each maven publication becomes a sorted entry with exact certainty`() {
        val document = PublicationDocument(
            group = "org.example",
            name = "lib",
            version = "1.0",
            publications = listOf(
                MavenPublicationDocument("org.example", "lib-jvm", "1.0"),
                MavenPublicationDocument("org.example", "lib", "1.0"),
            ),
        )
        assertEquals(
            mapOf("maven.publications" to "org.example:lib-jvm:1.0,org.example:lib:1.0"),
            PublicationAttributes.of(document),
        )
    }

    @Test
    fun `a project that applies the publishing plugin but declares no publication records nothing`() {
        val document = PublicationDocument(group = "org.example", name = "lib", version = "1.0")
        assertEquals(emptyMap(), PublicationAttributes.of(document))
    }

    @Test
    fun `an empty group or an unspecified version records nothing`() {
        assertEquals(emptyMap(), PublicationAttributes.of(PublicationDocument("", "lib", "1.0")))
        assertEquals(emptyMap(), PublicationAttributes.of(PublicationDocument("org.example", "lib", "unspecified")))
        assertEquals(emptyMap(), PublicationAttributes.of(null))
    }

    @Test
    fun `the document of the model plugin carries the publication section`() {
        val json = """[{"schema":1,"path":":lib","dir":"/w/lib","buildDir":"/w/lib/build",
            "build":{"name":"w","rootDir":"/w"},
            "publication":{"group":"g","name":"lib","version":"1",
            "publications":[{"groupId":"g","artifactId":"a","version":"1"}]}}]"""

        val document = ModelDocuments.parse(json).single()

        val expected = PublicationDocument("g", "lib", "1", listOf(MavenPublicationDocument("g", "a", "1")))
        assertEquals(expected, document.publication)
    }
}
