package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetRole
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmLibraryContentTest {
    private val workspace = JvmLibraryWorkspace()

    init {
        workspace.draft.dependsOnFile(workspace.client, "client/libs/text-1.0.0.jar")
    }

    @AfterTest
    fun clean() = workspace.close()

    private fun link(): Relationship = workspace.links().relationships.single()

    @Test
    fun `a JAR whose classes are the sources of the module is content evidence and exact`() {
        workspace.textSources("sample/Slugs.kt", "sample/Dates.kt")
        workspace.consumerJar(
            "META-INF/MANIFEST.MF",
            "sample/Slugs.class",
            "sample/Slugs\$Inner.class",
            "sample/DatesKt.class",
        )

        val edge = link()

        assertEquals(EvidenceClass.CONTENT, edge.evidenceClass)
        assertEquals(Certainty.EXACT, (edge.resolution as Resolution.Resolved).certainty)
        assertEquals("matched", edge.attributes["content"])
        assertEquals(NodeRef.OfModule(workspace.text), (edge.resolution as Resolution.Resolved).target)
    }

    @Test
    fun `Java sources count as the sources of the module`() {
        workspace.textSources("sample/Slugs.java")
        workspace.consumerJar("sample/Slugs.class")

        assertEquals("matched", link().attributes["content"])
    }

    @Test
    fun `a class in a package that the sources do not have makes the JAR differ`() {
        workspace.textSources("sample/Slugs.kt")
        workspace.consumerJar("sample/Slugs.class", "other/Extra.class")

        val edge = link()

        assertEquals(EvidenceClass.BUILD_MODEL, edge.evidenceClass)
        assertEquals(Certainty.HEURISTIC, (edge.resolution as Resolution.Resolved).certainty)
        assertEquals("differs", edge.attributes["content"])
        assertEquals(NodeRef.OfModule(workspace.text), (edge.resolution as Resolution.Resolved).target)
    }

    @Test
    fun `a JAR without a class that a source names differs`() {
        workspace.textSources("sample/Slugs.kt")
        workspace.consumerJar("sample/Other.class")

        assertEquals("differs", link().attributes["content"])
    }

    @Test
    fun `a JAR without classes differs`() {
        workspace.textSources("sample/Slugs.kt")
        workspace.consumerJar("META-INF/MANIFEST.MF")

        assertEquals("differs", link().attributes["content"])
    }

    @Test
    fun `the sources of tests do not make a JAR match`() {
        workspace.textSources("sample/Slugs.kt")
        workspace.draft.sources(
            workspace.text,
            SourceSetRole.UNIT_TEST,
            "text/src/test/kotlin",
            listOf("sample/SlugsTest.kt"),
        )
        workspace.consumerJar("sample/SlugsTest.class")

        assertEquals("differs", link().attributes["content"])
    }

    @Test
    fun `no JAR on disk leaves the link on the build model alone`() {
        workspace.textSources("sample/Slugs.kt")

        val edge = link()

        assertEquals(EvidenceClass.BUILD_MODEL, edge.evidenceClass)
        assertEquals(Certainty.HEURISTIC, (edge.resolution as Resolution.Resolved).certainty)
        assertNull(edge.attributes["content"])
    }

    @Test
    fun `a JAR that cannot be read counts as no JAR`() {
        workspace.textSources("sample/Slugs.kt")
        Files.createDirectories(workspace.base.resolve("application/client/libs"))
        Files.writeString(workspace.base.resolve("application/client/libs/text-1.0.0.jar"), "not an archive")

        val edge = link()

        assertEquals(EvidenceClass.BUILD_MODEL, edge.evidenceClass)
        assertNull(edge.attributes["content"])
    }
}
