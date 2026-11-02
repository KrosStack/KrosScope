package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmLibraryAarTest {
    private val workspace = JvmLibraryWorkspace()

    init {
        workspace.textSources("sample/Slugs.kt")
        workspace.draft.dependsOnFile(workspace.client, "client/libs/text-1.0.0.jar")
    }

    @AfterTest
    fun clean() = workspace.close()

    private fun aar(name: String, vararg classes: String) =
        JarFixtures.writeAar(workspace.base.resolve("library/text/build/outputs/aar/$name"), *classes)

    private fun link(): Relationship = workspace.links().relationships.single()

    @Test
    fun `an AAR in the build outputs of the module confirms the link by content`() {
        aar("text-release.aar", "sample/Slugs.class")

        val edge = link()

        assertEquals(EvidenceClass.CONTENT, edge.evidenceClass)
        assertEquals(Certainty.EXACT, (edge.resolution as Resolution.Resolved).certainty)
        assertEquals("matched", edge.attributes["content"])
    }

    @Test
    fun `an AAR whose classes are not those of the sources differs`() {
        aar("text-release.aar", "other/Extra.class")

        assertEquals("differs", link().attributes["content"])
    }

    @Test
    fun `only AARs named after the module are read`() {
        aar("other-release.aar", "sample/Slugs.class")

        assertNull(link().attributes["content"])
    }

    @Test
    fun `a debug and a release AAR of the module are read in name order`() {
        aar("text-debug.aar", "other/Extra.class")
        aar("text-release.aar", "sample/Slugs.class")

        assertEquals("differs", link().attributes["content"])
    }

    @Test
    fun `a JAR in build libs is read before an AAR`() {
        workspace.builtJar("sample/Slugs.class")
        aar("text-release.aar", "other/Extra.class")

        assertEquals("matched", link().attributes["content"])
    }

    @Test
    fun `an AAR that cannot be read counts as no archive`() {
        val broken = workspace.base.resolve("library/text/build/outputs/aar/text-release.aar")
        Files.createDirectories(broken.parent)
        Files.writeString(broken, "not an archive")

        assertNull(link().attributes["content"])
    }
}
