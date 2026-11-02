package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import java.io.File

/** Source directories as `/`-separated absolute paths, reduced to those that exist. */
internal object SourceDirs {
    fun existing(files: Iterable<File>): List<String> =
        files.filter { it.exists() }.map(Values::slashPath).distinct().sorted()

    fun of(directorySet: Any?): List<String> = existing((directorySet as? SourceDirectorySet)?.srcDirs.orEmpty())

    fun ofRelative(project: Project, directories: Any?): List<String> =
        existing(Values.items(directories).map { project.file(it.toString()) })
}
