package io.github.krosstack.kscope.engine.build.archive.folders

import java.nio.file.Files
import java.nio.file.Path

/** The layout of a Maven repository: `group/as/directories/artifact/version/artifact-version.jar`. */
internal object MavenLayout : FolderLayout {
    override fun archives(folder: Path, coordinate: MavenCoordinate): List<Path> {
        val directory = coordinate.groupDirectories.fold(folder) { path, segment -> path.resolve(segment) }
            .resolve(coordinate.artifact).resolve(coordinate.version)
        return listOf(coordinate.jarName, coordinate.aarName).map(directory::resolve).filter(Files::isRegularFile)
    }
}
