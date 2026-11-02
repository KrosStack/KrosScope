package io.github.krosstack.kscope.analyzer.gradle.tooling

import io.github.krosstack.kscope.analyzer.gradle.ProviderWorkspace
import io.github.krosstack.kscope.analyzer.gradle.WorkspaceRequests
import io.github.krosstack.kscope.model.ProducerRef
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleJavaHomeTest {
    private val directory = Files.createTempDirectory("kscope-java-home")

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `the variable names the directory of the JDK that Gradle runs with`() {
        val home = GradleJavaHome.from(mapOf("KSCOPE_GRADLE_JAVA_HOME" to "  /opt/jdk-21 ")::get)

        assertEquals(Path.of("/opt/jdk-21"), home)
    }

    @Test
    fun `without the variable, or with a blank one, Gradle keeps the JDK of the build`() {
        assertNull(GradleJavaHome.from(emptyMap<String, String>()::get))
        assertNull(GradleJavaHome.from(mapOf("KSCOPE_GRADLE_JAVA_HOME" to "  ")::get))
    }

    @Test
    fun `a Java home that is not a directory is rejected before Gradle is started`() {
        val missing = directory.resolve("no-jdk")
        val plugin = directory.resolve("plugin.jar")
        val request = FetchRequest(directory, plugin, directory.resolve("init.gradle"), missing)

        val message = assertIs<FetchOutcome.Rejected>(ToolingApiFetcher().fetch(request)).message

        assertTrue("KSCOPE_GRADLE_JAVA_HOME" in message && missing.toString() in message, message)
    }

    @Test
    fun `the Java home of Gradle is part of the fingerprint of the model`() {
        ProviderWorkspace().use { workspace ->
            workspace.givenSimpleBuild()
            val request = WorkspaceRequests.of(workspace.root)
            val assets = PluginAssets.extract(directory.resolve("model"))
            val producer = ProducerRef("kscope.build.gradle.tooling", "1")
            val first = directory.resolve("jdk-17").also(Files::createDirectories)
            val second = directory.resolve("jdk-21").also(Files::createDirectories)

            fun fingerprint(home: Path?) = BuildInputs(request.repository.id, workspace.root, directory, home)
                .fingerprint(producer, request.artifacts, assets)

            assertNotEquals(fingerprint(null), fingerprint(first))
            assertNotEquals(fingerprint(first), fingerprint(second))
            assertEquals(fingerprint(first), fingerprint(first))
        }
    }

    @Test
    fun `the Java home reaches the fetch and an upgrade of that JDK in place fetches the model again`() {
        val home = directory.resolve("jdk").also(Files::createDirectories)
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"17.0.1\"")
        ProviderWorkspace(javaHome = home).use { workspace ->
            workspace.givenSimpleBuild()
            workspace.provide()
            assertEquals(listOf<Path?>(home), workspace.javaHomes)

            Files.writeString(home.resolve("release"), "JAVA_VERSION=\"21.0.1\"")
            workspace.provide()

            assertEquals(2, workspace.fetches.size)
        }
    }
}
