package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.EvidenceClass
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmLibraryLocationTest {
    private val workspace = JvmLibraryWorkspace()

    @AfterTest
    fun clean() = workspace.close()

    @Test
    fun `the JAR is read from the build output of the module when the consumer has none`() {
        workspace.textSources("sample/Slugs.kt")
        workspace.builtJar("sample/Slugs.class")
        workspace.draft.dependsOnFile(workspace.client, "client/libs/text-1.0.0.jar")

        val edge = workspace.links().relationships.single()

        assertEquals(EvidenceClass.CONTENT, edge.evidenceClass)
        assertEquals("matched", edge.attributes["content"])
    }

    @Test
    fun `the JAR of the consumer is read before the build output of the module`() {
        workspace.textSources("sample/Slugs.kt")
        workspace.consumerJar("other/Extra.class")
        workspace.builtJar("sample/Slugs.class")
        workspace.draft.dependsOnFile(workspace.client, "client/libs/text-1.0.0.jar")

        assertEquals("differs", workspace.links().relationships.single().attributes["content"])
    }

    @Test
    fun `a path that leaves the repository of the consumer is looked at beside it`() {
        JarFixtures.write(workspace.base.resolve("shared/text-1.0.0.jar"), "sample/Slugs.class")
        workspace.textSources("sample/Slugs.kt")
        workspace.draft.dependsOnFile(workspace.client, "../shared/text-1.0.0.jar")

        assertEquals("matched", workspace.links().relationships.single().attributes["content"])
    }
}
