package io.github.krosstack.kscope.engine.build.archive

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmLibraryTreeChangeTest {
    private val workspace = JvmLibraryWorkspace()

    @AfterTest
    fun clean() = workspace.close()

    private fun vendor() = JarFixtures.write(workspace.base.resolve("application/client/libs/text-1.0.0.jar"))

    private fun linkedFiles() = workspace.links().relationships.map { it.attributes["file"] }

    @Test
    fun `a JAR that appears in the tree after an earlier resolution is linked by the next one`() {
        workspace.draft.dependsOnFile(workspace.client, "client/libs/")
        assertEquals(emptyList(), linkedFiles())

        vendor()

        assertEquals(listOf("text-1.0.0.jar"), linkedFiles())
    }

    @Test
    fun `a JAR that leaves the tree is no longer linked by the next resolution`() {
        val jar = vendor()
        workspace.draft.dependsOnFile(workspace.client, "client/libs/")
        assertEquals(listOf("text-1.0.0.jar"), linkedFiles())

        jar.toFile().delete()

        assertEquals(emptyList(), linkedFiles())
    }
}
