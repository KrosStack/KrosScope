package io.github.krosstack.kscope.engine.build.archive

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmLibraryTreeTest {
    private val workspace = JvmLibraryWorkspace()

    @AfterTest
    fun clean() = workspace.close()

    private fun vendor(path: String) = JarFixtures.write(workspace.base.resolve("application/client/libs/$path"))

    private fun linkedFiles(attributes: Map<String, String> = emptyMap()): List<String?> {
        workspace.draft.dependsOnFile(workspace.client, "client/libs/", attributes = attributes)
        return workspace.links().relationships.map { it.attributes["file"] }
    }

    @Test
    fun `a JAR in a subdirectory of the tree is linked and read from where it lies`() {
        workspace.textSources("sample/Slugs.kt")
        JarFixtures.write(workspace.base.resolve("application/client/libs/nested/text-1.0.0.jar"), "sample/Slugs.class")

        workspace.draft.dependsOnFile(workspace.client, "client/libs/")

        val edge = workspace.links().relationships.single()

        assertEquals("text-1.0.0.jar", edge.attributes["file"])
        assertEquals("matched", edge.attributes["content"])
    }

    @Test
    fun `the include patterns of the tree select the JARs that count`() {
        vendor("text-1.0.0.jar")
        assertEquals(listOf("text-1.0.0.jar"), linkedFiles(mapOf("tree.include" to "*.jar")))
    }

    @Test
    fun `a JAR that no include pattern selects is not linked`() {
        vendor("text-1.0.0.jar")
        assertEquals(emptyList(), linkedFiles(mapOf("tree.include" to "selected.jar")))
    }

    @Test
    fun `a JAR that an exclude pattern names is not linked`() {
        vendor("text-1.0.0.jar")
        assertEquals(emptyList(), linkedFiles(mapOf("tree.include" to "*.jar", "tree.exclude" to "text-*.jar")))
    }

    @Test
    fun `a pattern is relative to the directory of the tree`() {
        vendor("nested/text-1.0.0.jar")
        assertEquals(emptyList(), linkedFiles(mapOf("tree.include" to "*.jar")))
    }

    @Test
    fun `an any depth pattern reaches into subdirectories`() {
        vendor("nested/deeper/text-1.0.0.jar")
        assertEquals(listOf("text-1.0.0.jar"), linkedFiles(mapOf("tree.include" to "**/*.jar")))
    }

    @Test
    fun `an excluded directory hides the JARs below it`() {
        vendor("nested/text-1.0.0.jar")
        assertEquals(emptyList(), linkedFiles(mapOf("tree.exclude" to "nested")))
    }
}
