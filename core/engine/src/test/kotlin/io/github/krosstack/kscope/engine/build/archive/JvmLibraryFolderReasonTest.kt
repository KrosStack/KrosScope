package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.build.ModuleSpec
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class JvmLibraryFolderReasonTest {
    private val workspace = JvmLibraryWorkspace()

    init {
        workspace.dependOnPublishedText()
    }

    @AfterTest
    fun clean() = workspace.close()

    @Test
    fun `a folder that holds no such archive reports the output of the module as missing`() {
        val edge = workspace.links(workspace.mavenLocal).relationships.single()
        val resolution = edge.resolution as Resolution.Unresolved

        assertEquals("artifact.output-missing", resolution.reason.value)
        assertEquals("text-1.0.0.jar", resolution.hint)
        assertEquals(NodeRef.OfModule(workspace.client), edge.from)
    }

    @Test
    fun `two modules that build the archive are reported as ambiguous and none is linked`() {
        workspace.draft.repository("fork")
        workspace.draft.module("fork", ":text", ModuleSpec(archives = "text-1.0.0.jar"))
        workspace.published(workspace.mavenLocal, "org/example/text/Slugs.class")

        val edge = workspace.links(workspace.mavenLocal).relationships.single()

        assertEquals("artifact.ambiguous-producer", (edge.resolution as Resolution.Unresolved).reason.value)
    }

    @Test
    fun `a version that is not a literal is not looked up`() {
        listOf("1.+", "[1.0,2.0)", "latest.release", "", null).forEach { version ->
            JvmLibraryWorkspace().use { other ->
                other.draft.dependsOn(other.client, "org.example:text", version)
                other.textSources("org/example/text/Slugs.kt")
                other.published(other.mavenLocal, "org/example/text/Slugs.class")

                assertEquals(emptyList(), other.links(other.mavenLocal).relationships, "$version")
            }
        }
    }

    @Test
    fun `a coordinate that would leave the folder is not looked up`() {
        JvmLibraryWorkspace().use { other ->
            listOf(
                Triple("..", "text", "1.0.0"),
                Triple("org.example", "..", "1.0.0"),
                Triple("org.example", "text", ".."),
                Triple("org/example", "text", "1.0.0"),
                Triple("org.example", "text\\x", "1.0.0"),
                Triple("org..example", "text", "1.0.0"),
            ).forEach { (group, artifact, version) ->
                other.draft.dependsOn(other.client, "$group:$artifact", version)
            }
            JarFixtures.write(other.base.resolve("text-1.0.0.jar"), "org/example/text/Slugs.class")

            assertEquals(emptyList(), other.links(other.mavenLocal).relationships)
        }
    }

    @Test
    fun `the archives that were looked up and the folders are inputs of the unit that keeps the links`() {
        val jar = workspace.published(workspace.mavenLocal, "org/example/text/Slugs.class")

        val before = workspace.links(workspace.mavenLocal).inputs
        JarFixtures.write(jar, "org/example/text/Slugs.class", "org/example/text/More.class")
        val after = workspace.links(workspace.mavenLocal).inputs

        assertEquals(2, before.size)
        assertEquals(before.single { "text-1.0.0.jar" !in it }, after.single { "text-1.0.0.jar" !in it })
        assertNotEquals(before.single { "text-1.0.0.jar" in it }, after.single { "text-1.0.0.jar" in it })
    }

    @Test
    fun `a registered folder is an input even when nothing is found in it`() {
        val inputs = workspace.links(workspace.mavenLocal).inputs

        assertEquals(1, inputs.size)
        assertEquals(true, "m2" in inputs.single())
    }
}
