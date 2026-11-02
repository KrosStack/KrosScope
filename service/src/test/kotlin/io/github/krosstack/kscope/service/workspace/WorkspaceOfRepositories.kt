package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.service.TestGit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText

/**
 * `fixtures/workspace-of-repositories` as six Git repositories under a temporary root. With [withOutputs] it also
 * writes what a build of them leaves on disk, from `benchmark/workspace/outputs.json`, after the commits;
 * [writeOutputs] does so later, as a build that runs after a scan.
 */
internal class WorkspaceOfRepositories(withOutputs: Boolean = false) : AutoCloseable {
    val root: Path = Files.createTempDirectory("kscope-repositories")
    val repositories: List<String>

    init {
        val fixture = Path.of(System.getProperty("kscope.workspaceFixture"))
        Files.walk(fixture).use { paths ->
            paths.filter(Files::isRegularFile).forEach { file ->
                val target = root.resolve(fixture.relativize(file).toString())
                Files.createDirectories(target.parent)
                Files.copy(file, target)
            }
        }
        repositories = Files.list(root).use { paths ->
            paths.filter(Files::isDirectory).map { it.fileName.toString() }.sorted().toList()
        }
        repositories.forEach(::commit)
        if (withOutputs) writeOutputs()
    }

    override fun close() {
        root.toFile().deleteRecursively()
    }

    private fun commit(repository: String) {
        val directory = root.resolve(repository)
        TestGit.run(directory, listOf("init", "-q"))
        TestGit.run(directory, listOf("add", "-A"))
        TestGit.run(directory, listOf("commit", "-q", "-m", "initial"))
    }

    fun writeOutputs() {
        val text = Path.of(System.getProperty("kscope.workspaceOutputs")).readText()
        JSON.decodeFromString(Outputs.serializer(), text).outputs.forEach { output ->
            val target = root.resolve(output.path)
            Files.createDirectories(target.parent)
            if (output.kind == JAR) writeJar(target, output.entries) else Files.writeString(target, output.text)
        }
    }

    private fun writeJar(target: Path, entries: List<String>) {
        ZipOutputStream(Files.newOutputStream(target)).use { jar ->
            entries.forEach { entry ->
                jar.putNextEntry(ZipEntry(entry))
                if (entry == MANIFEST) jar.write("Manifest-Version: 1.0\n".toByteArray())
                jar.closeEntry()
            }
        }
    }

    @Serializable
    private data class Outputs(val outputs: List<Output>)

    @Serializable
    private data class Output(
        val path: String,
        val kind: String,
        val entries: List<String> = emptyList(),
        val text: String = "",
    )

    private companion object {
        const val JAR = "jar"
        const val MANIFEST = "META-INF/MANIFEST.MF"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
