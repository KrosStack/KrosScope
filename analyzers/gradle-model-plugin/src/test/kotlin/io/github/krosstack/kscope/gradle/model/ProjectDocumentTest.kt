package io.github.krosstack.kscope.gradle.model

import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectDocumentTest {
    private val directory: Path = Files.createTempDirectory("kscope-project-document")

    private fun documentOf(configure: (org.gradle.api.Project) -> Unit): Map<String, Any?> {
        val projectDir = directory.resolve("lib").toFile().also { it.mkdirs() }
        val project = ProjectBuilder.builder()
            .withName("lib")
            .withProjectDir(projectDir)
            .withGradleUserHomeDir(directory.resolve("home").toFile())
            .build()
        configure(project)
        return ProjectDocument(project, KscopeProjectState.of(project)).build()
    }

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a java library document names the project, its plugins and its existing source directories`() {
        val document = documentOf { project ->
            project.plugins.apply("java-library")
            project.file("src/main/java").mkdirs()
        }
        assertEquals(SCHEMA_VERSION, document["schema"])
        assertEquals(":", document["path"])
        assertEquals(listOf("java", "java-library"), document["plugins"])
        val sourceSets = document["java"] as List<*>
        val main = sourceSets.map { it as Map<*, *> }.first { it["name"] == "main" }
        assertEquals(1, (main["javaDirs"] as List<*>).size)
        assertTrue((main["resourceDirs"] as List<*>).isEmpty())
        assertNull(document["kotlin"])
        assertNull(document["android"])
    }

    @Test
    fun `declared dependencies are listed with their configuration and plumbing configurations are left out`() {
        val document = documentOf { project ->
            project.plugins.apply("java-library")
            project.dependencies.add("api", "org.example:api-lib:1.2")
            project.dependencies.add("testImplementation", "org.example:test-lib:3")
        }
        val external = (document["dependencies"] as Map<*, *>)["external"] as List<*>
        val declared = external.map { (it as Map<*, *>).let { edge -> edge["configuration"] to edge["coordinates"] } }
        assertEquals(listOf("api" to "org.example:api-lib", "testImplementation" to "org.example:test-lib"), declared)
    }

    @Test
    fun `an android library names the aar it publishes, and not in a multiplatform project`() {
        val archives = listOf(false, true).map { multiplatform ->
            documentOf { project ->
                project.plugins.apply("com.android.library")
                if (multiplatform) project.plugins.apply("org.jetbrains.kotlin.multiplatform")
                project.version = "2.0.0"
            }["archives"]
        }
        assertEquals(listOf(listOf("lib-2.0.0.aar"), emptyList()), archives)
    }

    @Test
    fun `file dependencies list their files and the jar task names the archive`() {
        val document = documentOf { project ->
            project.plugins.apply("java-library")
            project.version = "1.0.0"
            project.extensions.getByType(org.gradle.api.plugins.BasePluginExtension::class.java)
                .archivesName.set("tasktide-text")
            project.dependencies.add("implementation", project.files("libs/a.jar"))
        }
        assertEquals(listOf("tasktide-text-1.0.0.jar"), document["archives"])
        val files = (document["dependencies"] as Map<*, *>)["files"] as List<*>
        val declared = files.map { (it as Map<*, *>).let { edge -> edge["configuration"] to edge["path"] } }
        assertEquals(listOf("implementation"), declared.map { it.first })
        assertTrue(declared.single().second.toString().endsWith("/lib/libs/a.jar"))
    }

    @Test
    fun `a file tree is recorded with its directory and patterns and not as the files it holds today`() {
        val document = documentOf { project ->
            project.plugins.apply("java-library")
            project.file("libs").mkdirs()
            project.file("libs/a.jar").writeText("")
            val patterns = mapOf("dir" to "libs", "include" to listOf("*.jar"), "exclude" to listOf("old-*.jar"))
            project.dependencies.add("implementation", project.fileTree(patterns))
        }
        val dependencies = document["dependencies"] as Map<*, *>
        val tree = (dependencies["trees"] as List<*>).single() as Map<*, *>
        assertEquals("implementation", tree["configuration"])
        assertTrue(tree["dir"].toString().endsWith("/lib/libs"))
        assertEquals(listOf("*.jar"), tree["include"])
        assertEquals(listOf("old-*.jar"), tree["exclude"])
        assertEquals(emptyList<Any>(), dependencies["files"])
    }

    @Test
    fun `the document is JSON that can be written and carries no errors for a healthy project`() {
        val document = documentOf { it.plugins.apply("java-library") }
        assertEquals(emptyMap<String, List<String>>(), document["errors"])
        val json = JsonWriter.write(document)
        assertTrue(json.startsWith("{\"schema\":$SCHEMA_VERSION,\"path\":\":\""))
    }
}
