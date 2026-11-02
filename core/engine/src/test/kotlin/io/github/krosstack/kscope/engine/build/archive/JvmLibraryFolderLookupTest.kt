package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.build.ModuleSpec
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmLibraryFolderLookupTest {
    private val workspace = JvmLibraryWorkspace()
    private val draft = workspace.draft

    init {
        workspace.dependOnPublishedText()
    }

    @AfterTest
    fun clean() = workspace.close()

    @Test
    fun `no folder registered means no archive is looked up`() {
        workspace.published(workspace.mavenLocal, "org/example/text/Slugs.class")

        assertEquals(emptyList(), workspace.links().relationships)
        assertEquals(emptyList(), workspace.links().inputs)
    }

    @Test
    fun `the first registered folder that holds the archive decides`() {
        val first = workspace.base.resolve("first")
        val second = workspace.base.resolve("second")
        workspace.published(first, "org/example/other/Extra.class")
        workspace.published(second, "org/example/text/Slugs.class")

        assertEquals("differs", workspace.links(first, second).relationships.single().attributes["content"])
        assertEquals("matched", workspace.links(second, first).relationships.single().attributes["content"])
    }

    @Test
    fun `a folder without the archive is skipped for the next one`() {
        workspace.published(workspace.mavenLocal, "org/example/text/Slugs.class")

        val edge = workspace.links(workspace.base.resolve("empty"), workspace.mavenLocal).relationships.single()

        assertEquals("matched", edge.attributes["content"])
    }

    @Test
    fun `an unreadable archive in a folder falls back to the build output of the producer`() {
        val jar = workspace.mavenLocal.resolve("org/example/text/1.0.0/text-1.0.0.jar")
        Files.createDirectories(jar.parent)
        Files.writeString(jar, "not an archive")
        workspace.builtJar("org/example/text/Slugs.class")

        assertEquals("matched", workspace.links(workspace.mavenLocal).relationships.single().attributes["content"])
    }

    @Test
    fun `a coordinate that a module of another repository publishes is left to the coordinate link`() {
        draft.module("library", ":kit", ModuleSpec(publications = "org.example:text:1.0.0"))
        workspace.published(workspace.mavenLocal, "org/example/text/Slugs.class")

        assertEquals(emptyList(), workspace.links(workspace.mavenLocal).relationships)
    }

    @Test
    fun `an archive that no module builds stays an external dependency`() {
        draft.dependsOn(workspace.client, "org.example:vendored", "3.2")
        JarFixtures.write(workspace.mavenLocal.resolve("org/example/vendored/3.2/vendored-3.2.jar"), "x/Y.class")

        val files = workspace.links(workspace.mavenLocal).relationships.map { it.attributes["file"] }

        assertEquals(listOf("text-1.0.0.jar"), files)
    }

    @Test
    fun `an archive is not matched inside the repository of the consumer`() {
        draft.module("application", ":sibling", ModuleSpec(archives = "sibling-1.0.jar"))
        draft.dependsOn(workspace.client, "org.example:sibling", "1.0")
        JarFixtures.write(workspace.mavenLocal.resolve("org/example/sibling/1.0/sibling-1.0.jar"), "x/Y.class")
        workspace.published(workspace.mavenLocal, "org/example/text/Slugs.class")

        val files = workspace.links(workspace.mavenLocal).relationships.map { it.attributes["file"] }

        assertEquals(listOf("text-1.0.0.jar"), files)
    }
}
