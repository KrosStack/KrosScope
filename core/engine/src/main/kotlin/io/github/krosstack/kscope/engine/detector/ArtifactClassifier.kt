package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Classifies a file with the first detector, in id order, that recognises it. */
internal class ArtifactClassifier(private val detectors: List<ArtifactDetector>, private val root: Path) {
    fun classify(path: RepoPath, sizeBytes: Long): ArtifactType {
        val head by lazy { readHead(path) }
        val candidate = ArtifactCandidate(path, sizeBytes) { head }
        return detectors.firstNotNullOfOrNull { it.detect(candidate) } ?: UNKNOWN
    }

    private fun readHead(path: RepoPath): ByteArray = try {
        Files.newInputStream(root.resolve(path.value)).use { it.readNBytes(HEAD_BYTES) }
    } catch (_: IOException) {
        ByteArray(0)
    }

    companion object {
        val UNKNOWN = ArtifactType("unknown", ArtifactCategory.OTHER)
        private const val HEAD_BYTES = 4096
    }
}
