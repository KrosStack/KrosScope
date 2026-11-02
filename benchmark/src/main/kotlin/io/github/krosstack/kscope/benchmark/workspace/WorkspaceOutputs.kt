package io.github.krosstack.kscope.benchmark.workspace

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText

@Serializable
internal data class BuildOutputs(val outputs: List<BuildOutput>)

/** A file that a build leaves on disk: a JAR with the listed [entries], or a text file holding [text]. */
@Serializable
internal data class BuildOutput(
    val path: String,
    val kind: String,
    val entries: List<String> = emptyList(),
    val text: String = "",
)

/** Reads the list of build outputs of the workspace and writes the files it names below a root. */
internal object WorkspaceOutputs {
    private const val JAR = "jar"
    private const val MANIFEST = "META-INF/MANIFEST.MF"
    private const val MANIFEST_TEXT = "Manifest-Version: 1.0\n"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(file: Path): BuildOutputs = json.decodeFromString(BuildOutputs.serializer(), file.readText())

    fun write(root: Path, outputs: BuildOutputs) {
        outputs.outputs.forEach { output ->
            val target = root.resolve(output.path)
            Files.createDirectories(target.parent)
            if (output.kind == JAR) writeJar(target, output.entries) else Files.writeString(target, output.text)
        }
    }

    private fun writeJar(target: Path, entries: List<String>) {
        ZipOutputStream(Files.newOutputStream(target)).use { jar ->
            entries.forEach { entry ->
                jar.putNextEntry(ZipEntry(entry))
                if (entry == MANIFEST) jar.write(MANIFEST_TEXT.toByteArray())
                jar.closeEntry()
            }
        }
    }
}
