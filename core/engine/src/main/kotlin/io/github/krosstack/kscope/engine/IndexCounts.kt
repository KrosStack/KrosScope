package io.github.krosstack.kscope.engine

/** The sizes of the structure and of the diagnostics of an index, as the database counts them. */
public data class IndexCounts(
    public val repositories: Int,
    public val buildRoots: Int,
    public val modules: Int,
    public val sourceSets: Int,
    public val targets: Int,
    public val variants: Int,
    public val diagnostics: Int,
)
