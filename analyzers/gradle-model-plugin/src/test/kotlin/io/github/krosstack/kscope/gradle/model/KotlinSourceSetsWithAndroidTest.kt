package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class FakeKotlinTarget(val name: String, val platformType: String)

internal class FakeKotlinSourceSet(
    val name: String,
    val kotlin: SourceDirectorySet,
    val dependsOn: Set<FakeKotlinSourceSet> = emptySet(),
)

internal class FakeKotlinMultiplatform(val targets: List<FakeKotlinTarget>, val sourceSets: List<FakeKotlinSourceSet>)

internal class FakeKotlinAndroid(val target: FakeKotlinTarget, val sourceSets: List<FakeKotlinSourceSet>)

internal class FakeAndroidDsl(val namespace: String)

class KotlinSourceSetsWithAndroidTest {
    private val directory: Path = Files.createTempDirectory("kscope-kotlin-android")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun projectOf(): Project = ProjectBuilder.builder()
        .withName("lib")
        .withProjectDir(directory.resolve("lib").toFile().also { it.mkdirs() })
        .withGradleUserHomeDir(directory.resolve("home").toFile())
        .build()

    private fun Project.sourceSet(name: String, vararg parents: FakeKotlinSourceSet): FakeKotlinSourceSet {
        val directories = objects.sourceDirectorySet(name, name)
        directories.srcDir(file("src/$name/kotlin").also { it.mkdirs() })
        return FakeKotlinSourceSet(name, directories, parents.toSet())
    }

    private fun kotlinSourceSetsOf(document: Map<String, Any?>): List<Map<*, *>> =
        ((document["kotlin"] as Map<*, *>)["sourceSets"] as List<*>).map { it as Map<*, *> }

    private fun targetNamesOf(document: Map<String, Any?>): List<Any?> =
        ((document["kotlin"] as Map<*, *>)["targets"] as List<*>).map { (it as Map<*, *>)["name"] }

    @Test
    fun `a multiplatform project with the Android plugin keeps its Kotlin source sets and their hierarchy`() {
        val project = projectOf()
        val common = project.sourceSet("commonMain")
        val targets = listOf(FakeKotlinTarget("android", "androidJvm"), FakeKotlinTarget("jvm", "jvm"))
        val sourceSets = listOf(common, project.sourceSet("jvmMain", common), project.sourceSet("androidMain", common))
        project.extensions.add("kotlin", FakeKotlinMultiplatform(targets, sourceSets))
        project.extensions.add("android", FakeAndroidDsl("sample.lib"))

        val document = ProjectDocument(project, KscopeProjectState.of(project)).build()

        val described = kotlinSourceSetsOf(document)
        assertEquals(listOf("androidMain", "commonMain", "jvmMain"), described.map { it["name"] })
        assertEquals(listOf("commonMain"), described.first { it["name"] == "jvmMain" }["dependsOn"])
        assertEquals(1, (described.first { it["name"] == "commonMain" }["kotlinDirs"] as List<*>).size)
        assertEquals("dsl", (document["android"] as Map<*, *>)["kind"])
    }

    @Test
    fun `a plain Android project leaves the source sets to the Android section`() {
        val project = projectOf()
        val main = project.sourceSet("main")
        project.extensions.add("kotlin", FakeKotlinAndroid(FakeKotlinTarget("android", "androidJvm"), listOf(main)))
        project.extensions.add("android", FakeAndroidDsl("sample.app"))

        val document = ProjectDocument(project, KscopeProjectState.of(project)).build()

        assertTrue(kotlinSourceSetsOf(document).isEmpty())
        assertEquals(listOf("android"), targetNamesOf(document))
    }
}
