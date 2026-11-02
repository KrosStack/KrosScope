package io.github.krosstack.kscope.conventions

internal class PackageSizeLimits(
    private val defaultLimit: Int,
    private val directoryLimits: Map<String, Int> = emptyMap(),
) {
    fun violations(kotlinFilesPerDirectory: Map<String, Int>): List<String> =
        kotlinFilesPerDirectory.toSortedMap()
            .filter { (directory, count) -> count > limitOf(directory) }
            .map { (directory, count) ->
                "$directory holds $count Kotlin files, at most ${limitOf(directory)} are allowed"
            }

    private fun limitOf(directory: String) = directoryLimits[directory] ?: defaultLimit
}
