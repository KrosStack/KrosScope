package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class FakeVariant(
    val name: String,
    val buildType: String,
    val productFlavors: List<Pair<String, String>>,
    val namespace: String = "sample.app",
)

class AndroidVariantsTest {
    private val directory: Path = Files.createTempDirectory("kscope-android-variants")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    private fun projectOf(): Project = ProjectBuilder.builder()
        .withName("app")
        .withProjectDir(directory.resolve("app").toFile().also { it.mkdirs() })
        .withGradleUserHomeDir(directory.resolve("home").toFile())
        .build()

    private fun sourceSetsOf(variant: FakeVariant, vararg components: String): Map<*, *> {
        val project = projectOf()
        components.forEach { project.configurations.create(variant.name + it) }
        val state = KscopeProjectState.of(project).also { it.variants.add(variant) }
        val described = AndroidVariants(project, state).describeAll().single()
        return described["sourceSets"] as Map<*, *>
    }

    @Test
    fun `the source set of the combined flavors belongs to the variants of every component`() {
        val variant = FakeVariant("freePlayDebug", "debug", listOf("tier" to "free", "store" to "play"))
        val sourceSets = sourceSetsOf(variant, "UnitTestCompileClasspath", "AndroidTestCompileClasspath")
        assertEquals(
            setOf("main", "debug", "free", "play", "freePlay", "freePlayDebug"),
            (sourceSets["main"] as List<*>).toSet(),
        )
        assertEquals(
            setOf("test", "testDebug", "testFree", "testPlay", "testFreePlay", "testFreePlayDebug"),
            (sourceSets["unitTest"] as List<*>).toSet(),
        )
        assertEquals(
            setOf(
                "androidTest",
                "androidTestDebug",
                "androidTestFree",
                "androidTestPlay",
                "androidTestFreePlay",
                "androidTestFreePlayDebug",
            ),
            (sourceSets["androidTest"] as List<*>).toSet(),
        )
    }

    @Test
    fun `a variant with one flavor has no combined flavor source set`() {
        val variant = FakeVariant("demoDebug", "debug", listOf("tier" to "demo"))
        val sourceSets = sourceSetsOf(variant)
        assertEquals(listOf("main", "debug", "demo", "demoDebug"), sourceSets["main"])
    }

    @Test
    fun `three flavor dimensions give one combined source set of all flavors`() {
        val variant = FakeVariant("freePlayEuRelease", "release", listOf("a" to "free", "b" to "play", "c" to "eu"))
        val names = sourceSetsOf(variant)["main"] as List<*>
        assertEquals(setOf("main", "release", "free", "play", "eu", "freePlayEu", "freePlayEuRelease"), names.toSet())
    }
}
