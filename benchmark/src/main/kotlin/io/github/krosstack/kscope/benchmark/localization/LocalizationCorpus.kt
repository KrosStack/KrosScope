package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.model.Artifact
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal data class LocalizationCorpus(val documents: Map<String, String>, val summary: LocalizationCorpusSummary) {
    companion object {
        private const val MAX_TEXT_BYTES = 2L * 1024 * 1024

        fun load(
            workspace: Path,
            artifacts: List<Artifact>,
            names: WorkspaceNames = WorkspaceNames.SINGLE,
        ): LocalizationCorpus {
            val entries = artifacts.sortedBy { names.of(it.ref) }.map { artifact ->
                names.of(artifact.ref) to read(workspace, names.of(artifact.ref), artifact)
            }
            val documents = entries.mapNotNull { (path, read) ->
                (read as? TextRead.Content)?.let { path to it.text }
            }.toMap()
            return LocalizationCorpus(
                documents,
                LocalizationCorpusSummary(
                    readableTextFiles = documents.size,
                    generatedFiles = entries.count { it.second == TextRead.Generated },
                    oversizedFiles = entries.count { it.second == TextRead.Oversized },
                    binaryFiles = entries.count { it.second == TextRead.Binary },
                    unreadableFiles = entries.count { it.second == TextRead.Unreadable },
                ),
            )
        }

        private fun read(workspace: Path, name: String, artifact: Artifact): TextRead = when {
            artifact.generated != null -> TextRead.Generated
            artifact.sizeBytes > MAX_TEXT_BYTES -> TextRead.Oversized
            else -> readFile(workspace.resolve(name))
        }

        private fun readFile(path: Path): TextRead {
            if (!Files.isReadable(path)) return TextRead.Unreadable
            return try {
                val content = Files.readString(path, Charsets.UTF_8)
                if ('\u0000' in content) TextRead.Binary else TextRead.Content(content)
            } catch (_: java.nio.charset.CharacterCodingException) {
                TextRead.Binary
            } catch (_: IOException) {
                TextRead.Unreadable
            }
        }
    }
}

private sealed interface TextRead {
    data class Content(val text: String) : TextRead
    data object Generated : TextRead
    data object Oversized : TextRead
    data object Binary : TextRead
    data object Unreadable : TextRead
}
