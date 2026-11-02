package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ARCHIVES = "jvm.archives"

class ArchiveStaticTest {
    private val tree = StaticWorkspace("archives")
    private val provided = tree.provided()

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    private fun archivesOf(path: String) = provided.module(path).attributes[ARCHIVES]

    @Test
    fun `the archive name of the base extension and the version of the script name the jar`() {
        assertEquals("tasktide-text-1.0.0.jar", archivesOf(":text"))
    }

    @Test
    fun `archives base name and the version of gradle properties name the jar`() {
        assertEquals("shelf-format-2.0.0.jar", archivesOf(":format"))
    }

    @Test
    fun `an android library records the aar it publishes and a computed archive name records nothing`() {
        assertEquals("android-2.0.0.aar", archivesOf(":android"))
        assertEquals(null, archivesOf(":computed"))
    }

    @Test
    fun `file dependencies point at paths in the repository and keep the path as written`() {
        val files = provided.edgesOf(":consumer").map { edge ->
            val target = (edge.resolution as Resolution.Resolved).target as NodeRef.OfExternal
            Triple(edge.attributes["configuration"], target.coordinate, edge.attributes["path"])
        }
        assertEquals(
            listOf(
                Triple("implementation", "file:consumer/libs/", "libs"),
                Triple("implementation", "file:consumer/libs/tasktide-text-1.0.0.jar", "libs/tasktide-text-1.0.0.jar"),
                Triple("implementation", "file:vendor/shared.jar", "vendor/shared.jar"),
            ),
            files.sortedBy { it.second },
        )
    }

    @Test
    fun `a file tree carries its patterns on the edge and a plain file carries none`() {
        val patterns = provided.edgesOf(":consumer").associate { edge ->
            edge.attributes["path"] to (edge.attributes["tree.include"] to edge.attributes["tree.exclude"])
        }
        assertEquals("*.jar" to null, patterns["libs"])
        assertEquals(null to null, patterns["vendor/shared.jar"])
    }

    private val direct = "archivesBaseName = 'new'"
    private val block = "base { archivesName.set('new') }"

    private fun archivesOfScript(vararg lines: String): String? {
        tree.write("text/build.gradle", (listOf("plugins { id 'java-library' }") + lines).joinToString("\n"))
        return tree.provided().module(":text").attributes[ARCHIVES]
    }

    @Test
    fun `the archive name set directly after a base block wins`() {
        val jar = archivesOfScript("version = '1.0.0'", "base { archivesName.set('old') }", direct)
        assertEquals("new-1.0.0.jar", jar)
    }

    @Test
    fun `the archive name set in a base block after a direct setting wins`() {
        val jar = archivesOfScript("version = '1.0.0'", "archivesBaseName = 'old'", block)
        assertEquals("new-1.0.0.jar", jar)
    }

    @Test
    fun `the last of two assignments of the version names the jar in either form`() {
        val name = "base { archivesName = 'lib' }"
        assertEquals("lib-2.0.jar", archivesOfScript(name, "version = '1.0'", "version '2.0'"))
        assertEquals("lib-2.0.jar", archivesOfScript(name, "version '1.0'", "version = '2.0'"))
    }
}
