package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.TestGit
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path

internal object TaskRepositoriesScope {
    const val TASK = "change the task sync clock"

    fun <T> inTwo(block: (KrosScope) -> T): T = within(
        listOf("first" to listOf("TaskStore", "TaskReminders"), "second" to listOf("TaskSync", "TaskClock")),
        block,
    )

    fun <T> inTwoQualified(block: (KrosScope) -> T): T = within(
        listOf(
            "first" to listOf("tasks.TaskStore", "tasks.TaskReminders"),
            "second" to listOf("sync.TaskSync", "sync.TaskClock"),
        ),
        block,
    )

    fun <T> inOne(block: (KrosScope) -> T): T = within(listOf("only" to listOf("TaskStore", "TaskSync")), block)

    private fun <T> within(repositories: List<Pair<String, List<String>>>, block: (KrosScope) -> T): T {
        assumeTrue(TestGit.available(), "git is not available")
        val root = Files.createTempDirectory("kscope-narrowing")
        try {
            repositories.forEach { (name, classes) -> repository(root.resolve(name), classes) }
            Files.createDirectories(root.resolve("notes"))
            return KrosScope.open(root, OpenOptions(level = ScanLevel.SYNTAX)).use { scope ->
                scope.scan()
                block(scope)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun repository(directory: Path, classes: List<String>) {
        write(directory, "settings.gradle.kts", "include(\":lib\")\n")
        write(directory, "lib/build.gradle.kts", "")
        classes.forEach { write(directory, "lib/src/main/kotlin/${it.replace('.', '/')}.kt", source(it)) }
        TestGit.run(directory, listOf("init", "-q"))
        TestGit.run(directory, listOf("add", "-A"))
        TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
    }

    private fun source(qualifiedName: String): String {
        val packageName = qualifiedName.substringBeforeLast('.', "")
        val declaration = "class ${qualifiedName.substringAfterLast('.')}\n"
        return if (packageName.isEmpty()) declaration else "package $packageName\n\n$declaration"
    }

    private fun write(directory: Path, relative: String, text: String) {
        val file = directory.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }
}
