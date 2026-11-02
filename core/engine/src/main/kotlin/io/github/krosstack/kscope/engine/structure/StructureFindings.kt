package io.github.krosstack.kscope.engine.structure

/**
 * Paths are relative to the inspected directory and `/`-separated, `.` for the directory itself and `..` above it; a
 * path that cannot be written relative to it is absolute. [evidence] says which file or entry showed it.
 */
public data class GitCheckout(
    public val directory: String,
    public val kind: CheckoutKind,
    public val repositoryId: String,
    public val evidence: String,
)

public data class WorktreeFinding(public val directory: String, public val exists: Boolean, public val evidence: String)

public data class BuildFinding(
    public val directory: String,
    public val settingsFile: String,
    public val includedBuilds: List<String>,
)

public data class ProjectFinding(
    public val directory: String,
    public val ecosystem: String,
    public val markerFile: String,
)

public data class RepositoryFinding(public val directory: String, public val checkout: GitCheckout)

public data class StructureFindings(
    public val checkout: GitCheckout?,
    public val worktrees: List<WorktreeFinding>,
    public val builds: List<BuildFinding>,
    public val projects: List<ProjectFinding>,
    public val repositories: List<RepositoryFinding>,
    public val directoriesVisited: Int,
    public val ignoredDirectoriesSkipped: Int,
    public val ignoreRulesNotice: String?,
    public val truncated: Boolean,
)
