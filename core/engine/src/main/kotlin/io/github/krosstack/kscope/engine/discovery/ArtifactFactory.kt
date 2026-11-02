package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.detector.ArtifactClassifier
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository

internal class ArtifactFactory(private val classifier: ArtifactClassifier) {
    fun create(repository: Repository, relative: String, content: FileContent): Artifact {
        val path = RepoPath(relative)
        return Artifact(
            ref = ArtifactRef(repository.id, path),
            type = classifier.classify(path, content.sizeBytes),
            contentHash = content.hash,
            sizeBytes = content.sizeBytes,
            lineCount = content.lineCount,
            sourceSet = null,
            generated = null,
        )
    }

    fun unreadableNotice(relative: String, reason: String) = Diagnostic(
        severity = DiagnosticSeverity.WARNING,
        code = ReasonCode("discovery.unreadable-file"),
        message = "$relative could not be read and is not inventoried: $reason",
        location = null,
    )
}
