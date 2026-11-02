package io.github.krosstack.kscope.engine.build.archive.folders

import io.github.krosstack.kscope.engine.build.VersionState

/**
 * A Maven coordinate with a literal version, whose parts are plain names: none is empty, names a parent directory or
 * holds a separator, so that a path built from them stays below the folder it is looked up in.
 */
internal class MavenCoordinate private constructor(val group: String, val artifact: String, val version: String) {
    val jarName: String get() = "$artifact-$version.jar"
    val aarName: String get() = "$artifact-$version.aar"
    val groupDirectories: List<String> get() = group.split(GROUP_SEPARATOR)

    companion object {
        private const val GROUP_SEPARATOR = '.'
        private val groupSegment = Regex("[A-Za-z0-9_][A-Za-z0-9_+-]*")
        private val plainName = Regex("[A-Za-z0-9_][A-Za-z0-9._+-]*")

        /** The coordinate, or null when [version] is missing or not literal, or a part is not a plain name. */
        fun of(group: String, artifact: String, version: String?): MavenCoordinate? {
            val literal = version != null && !VersionState.isDynamic(version) && plainName.matches(version)
            val named = plainName.matches(artifact) && group.split(GROUP_SEPARATOR).all(groupSegment::matches)
            val plain = literal && named
            return if (plain) MavenCoordinate(group, artifact, checkNotNull(version)) else null
        }
    }
}
