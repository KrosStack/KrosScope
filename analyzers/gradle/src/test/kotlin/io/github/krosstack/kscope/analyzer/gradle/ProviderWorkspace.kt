package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.analyzer.gradle.tooling.FetchOutcome
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelResult
import java.nio.file.Files
import java.nio.file.Path

/** A temporary Gradle workspace, an index directory, and a provider whose fetches are scripted and recorded. */
internal class ProviderWorkspace(
    factsFrom: BuildModelProvider? = null,
    javaHome: Path? = null,
    sharedIndex: Path? = null,
) : AutoCloseable {
    private val base: Path = Files.createTempDirectory("kscope-gradle-provider")
    val root: Path = base.resolve("space").also { Files.createDirectories(it) }
    val index: Path = sharedIndex ?: base.resolve("index")
    val fetches = mutableListOf<Path>()
    val javaHomes = mutableListOf<Path?>()
    var outcome: (Path) -> FetchOutcome = { FetchOutcome.Fetched(documentsOf(root)) }

    private val provider = GradleToolingProvider(index, factsFrom, javaHome) { request ->
        fetches.add(request.projectDirectory)
        javaHomes.add(request.javaHome)
        outcome(request.projectDirectory)
    }

    fun write(path: String, text: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    fun provide(trusted: Boolean = true): BuildModelResult = provider.provide(WorkspaceRequests.of(root, trusted))

    fun givenSimpleBuild() {
        write("settings.gradle.kts", "include(\":app\")")
        write("app/build.gradle.kts", "plugins { kotlin(\"jvm\") }")
        write("app/src/main/kotlin/App.kt", "class App")
    }

    override fun close() {
        base.toFile().deleteRecursively()
    }
}
