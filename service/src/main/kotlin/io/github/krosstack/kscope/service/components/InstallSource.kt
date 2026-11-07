package io.github.krosstack.kscope.service.components

import java.nio.file.Path

public sealed interface InstallSource {
    public data object Network : InstallSource
    public data class Directory(public val path: Path) : InstallSource
    public data class Archive(public val path: Path) : InstallSource {
        internal fun accepts(files: List<ComponentFile>): Boolean = files.singleOrNull()?.path?.endsWith(".zip") == true
    }
}
