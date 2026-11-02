package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.service.registry.ArtifactFolderRegistration
import io.github.krosstack.kscope.service.registry.RegistrationOutcome
import io.github.krosstack.kscope.service.registry.RepositoryRegistration
import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import io.github.krosstack.kscope.service.trust.MachineDirectory
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertTrue

/**
 * An application `shop-app` whose module `:client` depends on `org.example:core:1.0`, a repository `core-kit` whose
 * module `:core` builds `core-1.0.jar` from `org.example.core.Slugs`, or `core-1.0.aar` when it is an [android]
 * library, and is registered with the application, and a Maven local repository, all below a temporary directory with
 * a registry of its own.
 */
internal class ArtifactFolderMachine(private val android: Boolean = false) : AutoCloseable {
    private val base: Path = Files.createTempDirectory("kscope-artifact-links").toRealPath()
    private val environment = mapOf(MachineDirectory.VARIABLE to base.resolve("home").toString())::get
    private val registry = RepositoryRegistry.ofThisMachine(property = { null }, environment = environment)
    val mavenLocal: Path = base.resolve("m2")
    val app: Path

    init {
        app = committed(
            "shop-app",
            "settings.gradle.kts" to "rootProject.name = \"shop-app\"\n\ninclude(\":client\")\n",
            "build.gradle.kts" to "plugins {\n    kotlin(\"jvm\") version \"2.1.0\" apply false\n}\n",
            "client/build.gradle.kts" to "plugins {\n    kotlin(\"jvm\")\n}\n\n" +
                "dependencies {\n    implementation(\"org.example:core:1.0\")\n}\n",
            "client/src/main/kotlin/org/example/shop/Cart.kt" to "package org.example.shop\n\n" +
                "import org.example.core.Slugs\n\nclass Cart {\n    fun slug() = Slugs.of(\"cart\")\n}\n",
        )
        val kit = committed(
            "core-kit",
            "settings.gradle.kts" to "rootProject.name = \"core-kit\"\n\ninclude(\":core\")\n",
            "build.gradle.kts" to "plugins {\n    kotlin(\"jvm\") version \"2.1.0\" apply false\n}\n",
            "core/build.gradle.kts" to if (android) ANDROID_CORE else JVM_CORE,
            "core/src/main/kotlin/org/example/core/Slugs.kt" to "package org.example.core\n\n" +
                "object Slugs {\n    fun of(title: String) = title.lowercase()\n}\n",
        )
        val outcome = RepositoryRegistration(app, registry).add(kit.toString())
        assertTrue(outcome is RegistrationOutcome.Registered, "$outcome")
    }

    val folders: ArtifactFolderRegistration get() = ArtifactFolderRegistration(app, registry)

    fun open(): KrosScope = KrosScope.open(app, OpenOptions(), environment)

    /** Writes the JAR or AAR that `publishToMavenLocal` would leave, holding the class files [classes]. */
    fun publish(vararg classes: String) {
        val archive = mavenLocal.resolve("org/example/core/1.0/core-1.0.${if (android) "aar" else "jar"}")
        Files.createDirectories(archive.parent)
        Files.write(archive, if (android) zipOf(mapOf("classes.jar" to jarOf(classes))) else jarOf(classes))
    }

    private fun jarOf(classes: Array<out String>): ByteArray = zipOf(classes.associateWith { ByteArray(0) })

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    override fun close() {
        base.toFile().deleteRecursively()
    }

    private fun committed(name: String, vararg files: Pair<String, String>): Path {
        val directory = base.resolve(name)
        files.forEach { (path, text) ->
            Files.createDirectories(directory.resolve(path).parent)
            Files.writeString(directory.resolve(path), text)
        }
        TestGit.run(directory, listOf("init", "-q"))
        TestGit.run(directory, listOf("add", "-A"))
        TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
        return directory
    }

    private companion object {
        const val JVM_CORE = "plugins {\n    kotlin(\"jvm\")\n}\n\nversion = \"1.0\"\n"
        const val ANDROID_CORE = "plugins {\n    id(\"com.android.library\")\n}\n\nversion = \"1.0\"\n\n" +
            "android {\n    namespace = \"org.example.core\"\n}\n"
    }
}
