package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmLibraryFolderTest {
    private val workspace = JvmLibraryWorkspace()

    init {
        workspace.dependOnPublishedText()
    }

    @AfterTest
    fun clean() = workspace.close()

    private fun Relationship.target() = (resolution as Resolution.Resolved).target

    private fun Relationship.certainty() = (resolution as Resolution.Resolved).certainty

    private fun uiLink(vararg folders: Path): Relationship =
        workspace.links(*folders).relationships.single { it.attributes["file"] == "ui-2.0.aar" }

    @Test
    fun `a JAR in a Maven local repository whose classes are the sources of a module links the consumer to it`() {
        workspace.published(workspace.mavenLocal, "META-INF/MANIFEST.MF", "org/example/text/Slugs.class")

        val edge = workspace.links(workspace.mavenLocal).relationships.single()

        assertEquals("jvm-library", edge.subKind)
        assertEquals(NodeRef.OfModule(workspace.client), edge.from)
        assertEquals(NodeRef.OfModule(workspace.text), edge.target())
        assertEquals(EvidenceClass.CONTENT, edge.evidenceClass)
        assertEquals(Certainty.EXACT, edge.certainty())
        assertEquals("matched", edge.attributes["content"])
        assertEquals("text-1.0.0.jar", edge.attributes["file"])
        assertEquals("implementation", edge.attributes["configuration"])
    }

    @Test
    fun `an AAR in a Maven local repository is read through its classes JAR`() {
        val ui = workspace.uiModule()
        JarFixtures.writeAar(
            workspace.mavenLocal.resolve("org/example/ui/2.0/ui-2.0.aar"),
            "org/example/ui/Panel.class",
        )

        val edge = uiLink(workspace.mavenLocal)

        assertEquals(NodeRef.OfModule(ui), edge.target())
        assertEquals(EvidenceClass.CONTENT, edge.evidenceClass)
        assertEquals("matched", edge.attributes["content"])
    }

    @Test
    fun `an AAR whose classes are not those of the module differs`() {
        workspace.uiModule()
        JarFixtures.writeAar(workspace.mavenLocal.resolve("org/example/ui/2.0/ui-2.0.aar"), "org/other/Panel.class")

        val edge = uiLink(workspace.mavenLocal)

        assertEquals(EvidenceClass.BUILD_MODEL, edge.evidenceClass)
        assertEquals(Certainty.HEURISTIC, edge.certainty())
        assertEquals("differs", edge.attributes["content"])
    }

    @Test
    fun `a JAR in the Gradle cache is found below the Gradle user home, its modules-2 directory or files-2_1`() {
        val gradleHome = workspace.base.resolve("gradle-home")
        JarFixtures.write(
            gradleHome.resolve("caches/modules-2/files-2.1/org.example/text/1.0.0/0a1b2c/text-1.0.0.jar"),
            "org/example/text/Slugs.class",
        )

        val folders = listOf(
            gradleHome,
            gradleHome.resolve("caches/modules-2"),
            gradleHome.resolve("caches/modules-2/files-2.1"),
        )

        folders.forEach { folder ->
            val edge = workspace.links(folder).relationships.single()
            assertEquals(NodeRef.OfModule(workspace.text), edge.target(), "$folder")
            assertEquals(EvidenceClass.CONTENT, edge.evidenceClass, "$folder")
        }
    }

    @Test
    fun `an AAR in the Gradle cache is linked`() {
        val ui = workspace.uiModule()
        val cache = workspace.base.resolve("gradle-home/caches/modules-2/files-2.1")
        JarFixtures.writeAar(cache.resolve("org.example/ui/2.0/9f8e7d/ui-2.0.aar"), "org/example/ui/Panel.class")

        val edge = uiLink(cache)

        assertEquals(NodeRef.OfModule(ui), edge.target())
        assertEquals("matched", edge.attributes["content"])
    }

    @Test
    fun `a JAR that differs from the sources links on the build model alone`() {
        workspace.published(workspace.mavenLocal, "org/example/text/Slugs.class", "org/example/other/Extra.class")

        val edge = workspace.links(workspace.mavenLocal).relationships.single()

        assertEquals(EvidenceClass.BUILD_MODEL, edge.evidenceClass)
        assertEquals(Certainty.HEURISTIC, edge.certainty())
        assertEquals("differs", edge.attributes["content"])
    }
}
