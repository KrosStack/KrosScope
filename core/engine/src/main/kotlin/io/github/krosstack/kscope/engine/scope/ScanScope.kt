package io.github.krosstack.kscope.engine.scope

/**
 * Which files of a workspace a scan reads. A file is read when it matches [include] (every file when empty) and no
 * pattern of [exclude]; [include] and [exclude] only narrow the listing, they never add a file that the listing leaves
 * out. [tests] false drops the files of source sets whose role is not main. Files that match [generated] are marked as
 * generated and never handed to producers. Patterns follow [ScanPattern].
 */
public data class ScanScope(
    public val include: List<String> = emptyList(),
    public val exclude: List<String> = emptyList(),
    public val tests: Boolean = true,
    public val generated: List<String> = emptyList(),
) {
    internal val isDefault: Boolean get() = this == ALL || canonical() == ALL

    internal fun canonical(): ScanScope =
        ScanScope(include.distinct().sorted(), exclude.distinct().sorted(), tests, generated.distinct().sorted())

    public companion object {
        public val ALL: ScanScope = ScanScope()
    }
}
