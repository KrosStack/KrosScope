package io.github.krosstack.kscope.cli.text.format

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.WorkspaceLocations
import java.nio.file.Path

/**
 * What a renderer needs to know about the run: the workspace its paths are relative to, whether to emphasise, the
 * terminal [width] (null when the output is captured, which is never cut), how much to print, which [sections]
 * the user named, if any, and the [locations] that name a file by its workspace path.
 */
internal class TextContext(
    val workspace: Path,
    val emphasis: Emphasis,
    val width: Int? = null,
    val detail: DetailLevel = DetailLevel.STANDARD,
    val sections: Set<String>? = null,
) {
    var locations: WorkspaceLocations = WorkspaceLocations.SINGLE
        private set

    /** This context, with [locations] to name files by workspace path. */
    fun located(locations: WorkspaceLocations): TextContext =
        TextContext(workspace, emphasis, width, detail, sections).also { it.locations = locations }

    /** [file] as a person types it: its workspace path. */
    fun path(file: ArtifactRef): String = locations.pathOf(file)

    /** [location] relative to the workspace, with forward slashes, when it lies inside; otherwise unchanged. */
    fun relative(location: String): String {
        val root = workspace.toAbsolutePath().normalize()
        val target = runCatching { Path.of(location) }.getOrNull()?.takeIf { it.isAbsolute }?.normalize()
        return target?.takeIf { it.startsWith(root) }?.let { root.relativize(it).toString().replace('\\', '/') }
            ?: location
    }

    /** The sections to print: the ones the user named, otherwise the [defaults] of the detail level. */
    fun shown(defaults: (DetailLevel) -> Set<String>): Set<String> = sections ?: defaults(detail)
}
