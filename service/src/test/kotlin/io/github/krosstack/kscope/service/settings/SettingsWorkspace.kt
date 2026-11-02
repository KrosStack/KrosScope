package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.TemporaryTree
import io.github.krosstack.kscope.spi.graph.GraphStore
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** A small Gradle workspace with its index beside it, so that the workspace holds only what a test puts there. */
internal class SettingsWorkspace : AutoCloseable {
    private val base: Path = Files.createTempDirectory("kscope-settings")
    val root: Path = Files.createDirectories(base.resolve("workspace"))
    val index: Path = Files.createDirectories(base.resolve("index"))
    val indexFile: Path = index.resolve("index.db")

    init {
        write("settings.gradle.kts", "include(\":lib\")\n")
        write("lib/build.gradle.kts", "")
        write("lib/src/main/kotlin/Lib.kt", "class Lib\n")
        write("README.md", "# Sample\n")
        Files.writeString(index.resolve("repository-id"), "fixed-repository-id")
    }

    fun write(relativePath: String, text: String) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    }

    fun delete(relativePath: String) {
        Files.delete(root.resolve(relativePath))
    }

    fun options(): OpenOptions = OpenOptions(indexDirectory = index)

    fun <T> open(
        options: OpenOptions = options(),
        environment: (String) -> String? = { null },
        block: (KrosScope) -> T,
    ): T = KrosScope.open(root, options, environment).use(block)

    override fun close() {
        TemporaryTree.delete(base)
    }
}

internal fun KrosScope.canonicalDump(): String = CanonicalFacts.toJson((engine.graph() as GraphStore).batches())

internal fun KrosScope.searchDocuments() = (engine.graph() as GraphStore).searchDocuments()

internal fun KrosScope.artifactPaths(): List<String> = graph().artifacts().map { it.ref.path.value }.sorted()

internal fun sha256Of(text: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
