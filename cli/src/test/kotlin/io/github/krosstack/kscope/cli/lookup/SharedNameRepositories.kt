package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.writeFile
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal const val APPLICATION_SHARED = "application/lib/src/main/kotlin/Shared.kt"

internal fun <T> inSharedNameRepositories(block: (NestedWorkspace) -> T): T = inTemporaryFolder { base ->
    assumeTrue(gitAnswers(), "git is not available")
    val workspace = NestedWorkspace(base)
    repository(workspace.root.resolve("application"), listOf("Shared", "Twin", "other.Twin"))
    repository(workspace.root.resolve("library"), listOf("Shared", "Record", "Twin"))
    Files.createDirectories(workspace.root.resolve("notes"))
    scanned(workspace.root)
    block(workspace)
}

internal fun repository(directory: Path, classes: List<String>) {
    writeFile(directory, "settings.gradle.kts", "include(\":lib\")\n")
    writeFile(directory, "lib/build.gradle.kts", "")
    classes.forEach { writeFile(directory, "lib/src/main/kotlin/${it.replace('.', '/')}.kt", source(it)) }
    git(directory, listOf("init", "-q"))
    git(directory, listOf("add", "-A"))
    git(directory, listOf("-c", "user.name=test", "-c", "user.email=test@example.org", "commit", "-q", "-m", "initial"))
}

private fun source(qualifiedName: String): String {
    val packageName = qualifiedName.substringBeforeLast('.', "")
    val declaration = "class ${qualifiedName.substringAfterLast('.')}\n"
    return if (packageName.isEmpty()) declaration else "package $packageName\n\n$declaration"
}

internal fun git(directory: Path, arguments: List<String>) {
    val process = ProcessBuilder(listOf("git") + arguments).directory(directory.toFile())
        .redirectErrorStream(true).start()
    val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
    check(process.waitFor() == 0) { "git $arguments failed: $output" }
}

internal fun gitAnswers(): Boolean = try {
    val process = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
    process.inputStream.readAllBytes()
    process.waitFor() == 0
} catch (_: IOException) {
    false
}
