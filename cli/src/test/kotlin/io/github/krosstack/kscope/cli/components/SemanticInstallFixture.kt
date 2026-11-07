package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentEntry
import io.github.krosstack.kscope.service.components.ComponentFile
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.semantic.FakeEmbeddingMain
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class SemanticInstallFixture(root: Path, mode: String = "normal") {
    val directory = ComponentDirectory(root.resolve("home"))
    private val source = Files.createDirectories(root.resolve("source"))
    val worker = entry("semantic", mapOf("bundle.zip" to archive()))
    val model = entry(
        "semantic-model",
        mapOf(
            "model/model.json" to """{"name":"fake","revision":"test","dimensions":2,"quantization":"int8-dynamic"}"""
                .toByteArray(),
            "model/mode" to mode.toByteArray(),
        ),
    )
    val manifest = ComponentManifest(1, listOf(worker, model))

    fun command(): ComponentsCommand = ComponentsCommand(manifest, directory)

    fun install(name: String) = command().test(listOf("install", name, "--yes", "--from", source.toString()))

    fun stopped(): Boolean {
        val installed = requireNotNull(directory.installed(model.name))
        val pid = Files.readString(installed.directory.resolve("model/pid")).toLong()
        return ProcessHandle.of(pid).map {
            it.onExit().get(5, TimeUnit.SECONDS)
            !it.isAlive
        }.orElse(true)
    }

    private fun entry(name: String, contents: Map<String, ByteArray>): ComponentEntry = ComponentEntry(
        name,
        "1",
        "Apache-2.0",
        "LICENSE",
        "Semantic test component",
        contents.map { (path, bytes) ->
            val target = source.resolve(path)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
            ComponentFile(
                path,
                "any",
                listOf("https://example.test/$path"),
                bytes.size.toLong(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
            )
        },
    )

    private fun archive(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("lib/fake.jar").apply { time = 0 })
            zip.write(jar())
            zip.closeEntry()
        }
    }.toByteArray()

    private fun jar(): ByteArray {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.CLASS_PATH] = System.getProperty("kscope.semantic.test.classpath")
                .split(File.pathSeparator).joinToString(" ") { Path.of(it).toUri().toString() }
        }
        return ByteArrayOutputStream().also { bytes ->
            JarOutputStream(bytes, manifest).use { jar ->
                val fake = FakeEmbeddingMain::class.java
                val resources = listOf(
                    "io/github/krosstack/kscope/semantic/worker/Main.class",
                    fake.name.replace('.', '/') + ".class",
                )
                resources.forEach { resource ->
                    jar.putNextEntry(JarEntry(resource).apply { time = 0 })
                    requireNotNull(fake.classLoader.getResourceAsStream(resource)).use { it.copyTo(jar) }
                    jar.closeEntry()
                }
            }
        }.toByteArray()
    }
}
