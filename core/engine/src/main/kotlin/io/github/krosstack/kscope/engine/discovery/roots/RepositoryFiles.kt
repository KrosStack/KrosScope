package io.github.krosstack.kscope.engine.discovery.roots

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Files
import java.nio.file.Path

/** Reads the files of the one repository whose checkout is [root], whatever repository a reference names. */
internal class RepositoryFiles(private val root: Path) : WorkspaceFiles {
    override fun read(ref: ArtifactRef): ByteArray = Files.readAllBytes(absolutePath(ref))

    override fun absolutePath(ref: ArtifactRef): Path = root.resolve(ref.path.value)
}
