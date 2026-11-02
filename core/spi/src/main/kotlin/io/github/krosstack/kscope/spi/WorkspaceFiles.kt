package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.ArtifactRef
import java.nio.file.Path

/** Read-only content access handed to capabilities; implemented by the engine. */
public interface WorkspaceFiles {
    public fun read(ref: ArtifactRef): ByteArray

    /** For external tools only; the result is never stored in facts. */
    public fun absolutePath(ref: ArtifactRef): Path
}
