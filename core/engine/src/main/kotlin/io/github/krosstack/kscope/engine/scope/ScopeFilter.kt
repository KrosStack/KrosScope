package io.github.krosstack.kscope.engine.scope

/** Applies a [ScanScope] to relative paths. */
internal class ScopeFilter(scope: ScanScope) {
    private val include = scope.include.map(::ScanPattern)
    private val exclude = scope.exclude.map(::ScanPattern)
    private val generated = scope.generated.map(::ScanPattern)

    /**
     * Whether a scan reads [path]. A file outside the included paths is still read when [isBuildFile] says that the
     * build model needs it, unless an exclude pattern names it.
     */
    fun admits(path: String, isBuildFile: (String) -> Boolean): Boolean {
        if (exclude.any { it.matches(path) }) return false
        return include.isEmpty() || include.any { it.matches(path) } || isBuildFile(path)
    }

    fun marksGenerated(path: String): Boolean = generated.any { it.matches(path) }
}
