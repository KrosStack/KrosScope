package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClasspathSectionTest {
    private val directory: Path = Files.createTempDirectory("kscope-classpath-section")
    private val errors = SectionErrors()

    private fun javaProject(): Project {
        val project = ProjectBuilder.builder()
            .withProjectDir(directory.resolve("project").toFile().also { it.mkdirs() })
            .withGradleUserHomeDir(directory.resolve("home").toFile())
            .build()
        project.plugins.apply("java-library")
        return project
    }

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `the existing files of a compile classpath are interned and listed per source set`() {
        val project = javaProject()
        val jar = Files.createFile(directory.resolve("library.jar")).toFile()
        project.dependencies.add("implementation", project.files(jar))
        project.dependencies.add("testImplementation", project.files(jar, directory.resolve("absent.jar").toFile()))
        val section = ClasspathSection(project, errors)
        section.addJavaSourceSets()
        val description = section.describe()
        assertEquals(listOf(Values.slashPath(jar)), description["files"])
        val entries = description["entries"] as List<*>
        assertEquals(listOf("main", "test"), entries.map { (it as Map<*, *>)["name"] })
        assertEquals(listOf(listOf(0), listOf(0)), entries.map { (it as Map<*, *>)["files"] })
        assertTrue(errors.asMap().isEmpty())
    }

    @Test
    fun `a classpath that cannot be resolved is an error of the section and not a failure`() {
        val project = javaProject()
        project.dependencies.add("implementation", "org.example:missing:1.0")
        val section = ClasspathSection(project, errors)
        section.addJavaSourceSets()
        assertTrue((section.describe()["entries"] as List<*>).isEmpty())
        assertEquals(listOf("main", "test"), errors.asMap().getValue("classpaths").map { it.substringBefore(": ") })
    }
}
