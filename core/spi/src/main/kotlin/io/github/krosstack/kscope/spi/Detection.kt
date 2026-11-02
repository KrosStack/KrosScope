package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.RepoPath

/** [head] yields the first bytes of the file, read lazily. */
public class ArtifactCandidate(public val path: RepoPath, public val sizeBytes: Long, public val head: () -> ByteArray)

/** The classified [artifacts] of one repository, and the [files] that read their content. */
public class OutputRootRequest(public val artifacts: List<Artifact>, public val files: WorkspaceFiles)

/**
 * A directory of build outputs and the tool [generator] that writes into it. Discovery reads it from disk when it
 * exists, though Git ignores it, and labels every file below it with [label] as the generator of the file.
 */
public data class OutputRoot(public val directory: RepoPath, public val generator: String) {
    public val label: String get() = LABEL_PREFIX + generator

    public companion object {
        /** What the label of every file below an output root starts with; producers receive exactly these files. */
        public const val LABEL_PREFIX: String = "output:"
    }
}

public interface ArtifactDetector {
    public val id: String

    public fun detect(candidate: ArtifactCandidate): ArtifactType?

    /**
     * The directories of build outputs that the files this detector claims declare. Discovery reads them from disk
     * when they exist, and hands their files to producers, labelled as outputs, so that a unit that consumes them runs
     * again when a build changes them.
     */
    public fun outputRoots(request: OutputRootRequest): List<OutputRoot> = emptyList()
}
