package io.github.krosstack.kscope.engine.build.archive.folders

import java.nio.file.Path

/** One way of keeping published archives below a folder. */
internal interface FolderLayout {
    /** The files below [folder] that are the JAR or the AAR of [coordinate], in the order they are preferred. */
    fun archives(folder: Path, coordinate: MavenCoordinate): List<Path>
}
