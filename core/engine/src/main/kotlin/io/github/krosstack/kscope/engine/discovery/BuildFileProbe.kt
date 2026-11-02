package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.detector.ArtifactClassifier
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.RepoPath
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Tells whether a file is a build file, which the build model needs wherever it lies. */
internal class BuildFileProbe(private val classifier: ArtifactClassifier, private val root: Path) {
    fun isBuildFile(relativePath: String): Boolean {
        val size = try {
            Files.size(root.resolve(relativePath))
        } catch (_: IOException) {
            return false
        }
        return classifier.classify(RepoPath(relativePath), size).category == ArtifactCategory.BUILD
    }
}
