package io.github.krosstack.kscope.gradle.model.publication

import io.github.krosstack.kscope.gradle.model.KscopeProjectState
import io.github.krosstack.kscope.gradle.model.ProjectDocument
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicationSectionTest {
    private val directory: Path = Files.createTempDirectory("kscope-publication-section")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun projectOf(configure: (Project) -> Unit): Project {
        val project = ProjectBuilder.builder()
            .withName("lib")
            .withProjectDir(directory.resolve("lib").toFile().also { it.mkdirs() })
            .withGradleUserHomeDir(directory.resolve("home").toFile())
            .build()
        configure(project)
        return project
    }

    @Test
    fun `the group, name, version and each maven publication are described`() {
        val project = projectOf { project ->
            project.plugins.apply("maven-publish")
            project.group = "org.example"
            project.version = "1.0"
            val publications = project.extensions.getByType(PublishingExtension::class.java).publications
            publications.create("main", MavenPublication::class.java) { it.artifactId = "lib-main" }
        }

        val section = checkNotNull(PublicationSection.describe(project))

        assertEquals("org.example", section["group"])
        assertEquals("lib", section["name"])
        assertEquals("1.0", section["version"])
        assertEquals(
            listOf(mapOf("groupId" to "org.example", "artifactId" to "lib-main", "version" to "1.0")),
            section["publications"],
        )
    }

    @Test
    fun `a project with only the ivy publishing plugin has no section`() {
        assertNull(PublicationSection.describe(projectOf { it.plugins.apply("ivy-publish") }))
    }

    @Test
    fun `a project without the publishing extension has no section`() {
        assertNull(PublicationSection.describe(projectOf { }))
    }

    @Test
    fun `the project document carries the section`() {
        val project = projectOf { it.plugins.apply("maven-publish") }
        val document = ProjectDocument(project, KscopeProjectState.of(project)).build()
        assertEquals("lib", (document["publication"] as Map<*, *>)["name"])
    }
}
