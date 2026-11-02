package io.github.krosstack.kscope.cli

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal const val LIBRARY_RECORD = "library/model/src/main/kotlin/org/example/library/Record.kt"
internal const val APPLICATION_MAIN = "application/app/src/main/kotlin/org/example/app/Main.kt"

/** A workspace root of two Git repositories, `application` and `library`, each with a build and a class. */
internal fun <T> inTwoRepositories(block: (Path) -> T): T {
    assumeTrue(gitAvailable(), "git is not available")
    val root = Files.createTempDirectory("kscope-cli-workspace")
    try {
        repository(
            root,
            "library",
            mapOf(
                "settings.gradle.kts" to "rootProject.name = \"library\"\ninclude(\":model\")\n",
                "model/build.gradle.kts" to "",
                "model/src/main/kotlin/org/example/library/Record.kt" to
                    "package org.example.library\n\nclass Record\n",
                "model/src/test/kotlin/org/example/library/RecordTest.kt" to
                    "package org.example.library\n\nclass RecordTest {\n    fun created() = Record()\n}\n",
            ),
        )
        repository(
            root,
            "application",
            mapOf(
                "settings.gradle.kts" to "rootProject.name = \"application\"\ninclude(\":app\")\n",
                "app/build.gradle.kts" to "",
                "app/src/main/kotlin/org/example/app/Main.kt" to "package org.example.app\n\nfun main() {}\n",
            ),
        )
        return block(root)
    } finally {
        root.toFile().deleteRecursively()
    }
}

internal fun <T> inScannedTwoRepositories(block: (Path) -> T): T = inTwoRepositories { root ->
    check(runKscope("scan", "--workspace", root.toString()).statusCode == 0) { "the workspace did not scan" }
    block(root)
}

private fun repository(root: Path, name: String, files: Map<String, String>) {
    val directory = root.resolve(name)
    files.forEach { (path, text) -> writeFile(directory, path, text) }
    git(directory, "init", "-q")
    git(directory, "add", "-A")
    git(directory, "-c", "user.name=test", "-c", "user.email=test@example.org", "commit", "-q", "-m", "initial")
}

private fun git(directory: Path, vararg arguments: String) {
    val process = ProcessBuilder(listOf("git") + arguments).directory(directory.toFile())
        .redirectErrorStream(true).start()
    val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
    check(process.waitFor() == 0) { "git ${arguments.toList()} failed: $output" }
}

private fun gitAvailable(): Boolean = try {
    val process = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
    process.inputStream.readAllBytes()
    process.waitFor() == 0
} catch (_: IOException) {
    false
}
