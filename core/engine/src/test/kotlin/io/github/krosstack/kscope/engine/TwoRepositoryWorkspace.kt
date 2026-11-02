package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.spi.CapabilityRegistry
import java.nio.file.Files
import java.nio.file.Path

/** A workspace root that holds two small Git repositories, `application` and `library`, created on construction. */
internal class TwoRepositoryWorkspace : AutoCloseable {
    private val workspace = TestWorkspace()
    val root: Path get() = workspace.root
    val indexDirectory: Path get() = workspace.indexDirectory

    init {
        createRepository("library", LIBRARY_FILES)
        createRepository("application", APPLICATION_FILES)
    }

    fun engine(registry: CapabilityRegistry = coreRegistry()): ScanEngine = workspace.engine(registry)

    fun write(relativePath: String, text: String) = workspace.write(relativePath, text)

    fun createRepository(directory: String, files: Map<String, String>, commit: Boolean = true): Path {
        files.forEach { (path, text) -> workspace.write("$directory/$path", text) }
        val checkout = root.resolve(directory)
        Files.createDirectories(checkout)
        TestGit.output(checkout, "init", "-q")
        if (commit) {
            TestGit.output(checkout, "add", "-A")
            TestGit.output(checkout, "commit", "-q", "-m", "initial")
        }
        return checkout
    }

    override fun close() = workspace.close()

    companion object {
        val LIBRARY_FILES = mapOf(
            "settings.gradle.kts" to "rootProject.name = \"library\"\ninclude(\":model\")\n",
            "model/build.gradle.kts" to "plugins { kotlin(\"jvm\") }\n",
            "model/src/main/kotlin/org/example/library/Record.kt" to "package org.example.library\n\nclass Record\n",
        )

        val APPLICATION_FILES = mapOf(
            "settings.gradle.kts" to "rootProject.name = \"application\"\ninclude(\":app\")\n",
            "app/build.gradle.kts" to "plugins { kotlin(\"jvm\") }\n",
            "app/src/main/kotlin/org/example/app/Main.kt" to "package org.example.app\n\nfun main() {}\n",
        )
    }
}
