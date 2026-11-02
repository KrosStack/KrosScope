package io.github.krosstack.kscope.gradle.model

import org.gradle.api.plugins.ExtensionAware
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

open class FakeComposeExtension

open class FakeResourcesExtension {
    var packageOfResClass: String = ""
    var publicResClass: Boolean = false
}

class ComposeResourcesSectionTest {
    private val directory: Path = Files.createTempDirectory("kscope-compose-section")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun generatorsOf(configure: (org.gradle.api.Project) -> Unit): Map<*, *> {
        val project = ProjectBuilder.builder()
            .withName("shared")
            .withProjectDir(directory.resolve("shared").toFile().also { it.mkdirs() })
            .withGradleUserHomeDir(directory.resolve("home").toFile())
            .build()
        configure(project)
        val document = ProjectDocument(project, KscopeProjectState.of(project)).build()
        return document["generators"] as Map<*, *>
    }

    private fun applyCompose(project: org.gradle.api.Project, configure: FakeResourcesExtension.() -> Unit) {
        val compose = project.extensions.create("compose", FakeComposeExtension::class.java)
        val resources = (compose as ExtensionAware).extensions.create("resources", FakeResourcesExtension::class.java)
        resources.configure()
    }

    @Test
    fun `the package and visibility the build sets and the group of the project are described`() {
        val generators = generatorsOf { project ->
            project.group = "tasktide.ui"
            applyCompose(project) {
                packageOfResClass = "sample.ui.res"
                publicResClass = true
            }
        }

        val compose = generators["composeResources"] as Map<*, *>

        assertEquals("sample.ui.res", compose["packageOfResClass"])
        assertEquals(true, compose["publicResClass"])
        assertEquals("tasktide.ui", compose["group"])
    }

    @Test
    fun `an unset package is described as absent`() {
        val generators = generatorsOf { project -> applyCompose(project) {} }

        val compose = generators["composeResources"] as Map<*, *>

        assertNull(compose["packageOfResClass"])
        assertEquals(false, compose["publicResClass"])
    }

    @Test
    fun `a project without the compose extension has no description`() {
        assertNull(generatorsOf { }["composeResources"])
    }
}
