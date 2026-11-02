package io.github.krosstack.kscope.analyzer.gradle.statics

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The build facts that the synthesizer of the Compose Multiplatform resource class reads. */
class ComposeResourcesStaticTest {
    private val tree = StaticWorkspace("compose-resources")
    private val model = tree.provided()

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun attributesOf(path: String) =
        model.model.modules.single { it.path == path }.attributes.filterKeys { it.startsWith("compose.resources.") }

    @Test
    fun `the package and the visibility of the resource class are read from the compose resources block`() {
        val attributes = attributesOf(":ui:shared")
        assertEquals("sample.ui.resources", attributes["compose.resources.packageOfResClass"])
        assertEquals("true", attributes["compose.resources.publicResClass"])
    }

    @Test
    fun `a module without an explicit package has the group gradle gives it`() {
        assertEquals("cmp.ui", attributesOf(":ui:shared")["compose.resources.group"])
        assertEquals(mapOf("compose.resources.group" to "cmp"), attributesOf(":desktop"))
    }

    @Test
    fun `a group that the script sets wins over the default group and a block nested in compose is read`() {
        assertEquals(mapOf("compose.resources.group" to "com.acme"), attributesOf(":explicit"))
    }

    @Test
    fun `a module that does not apply the plugin has no such attributes`() {
        assertEquals(emptyMap(), attributesOf(":plain"))
    }
}
