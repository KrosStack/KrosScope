package io.github.krosstack.kscope.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a directory is, in the terms of `kscope info --structure`. */
@Serializable
public enum class StructureKind {
    @SerialName("single-build")
    SINGLE_BUILD,

    @SerialName("several-builds")
    SEVERAL_BUILDS,

    @SerialName("several-repositories")
    SEVERAL_REPOSITORIES,

    @SerialName("none")
    NONE,
}

@Serializable
public enum class CheckoutRole {
    @SerialName("repository")
    REPOSITORY,

    @SerialName("linked-worktree")
    LINKED_WORKTREE,

    @SerialName("submodule")
    SUBMODULE,
}

@Serializable
public enum class BuildRole {
    @SerialName("top-level")
    TOP_LEVEL,

    @SerialName("included")
    INCLUDED,

    @SerialName("nested-independent")
    NESTED_INDEPENDENT,
}

/** [evidence] lists the findings that the [summary] rests on. */
@Serializable
public data class StructureConclusion(
    public val kind: StructureKind,
    public val summary: String,
    public val evidence: List<String>,
)

/** The Git checkout that holds the directory; [directory] is where its `.git` entry was found. */
@Serializable
public data class GitReport(
    public val directory: String,
    public val role: CheckoutRole,
    public val linkedWorktree: Boolean,
    public val evidence: String,
)

@Serializable
public data class WorktreeReport(public val directory: String, public val exists: Boolean, public val evidence: String)

/** [includedBy] names the build that includes this one; [includedBuilds] are the builds that it includes. */
@Serializable
public data class GradleBuildReport(
    public val directory: String,
    public val settingsFile: String,
    public val role: BuildRole,
    public val includedBy: String?,
    public val includedBuilds: List<String>,
    public val evidence: String,
)

@Serializable
public data class ProjectReport(
    public val directory: String,
    public val ecosystem: String,
    public val markerFile: String,
    public val evidence: String,
)

/** [sameRepositoryAs] is set when this checkout belongs to a repository that another listed checkout also shows. */
@Serializable
public data class NestedRepositoryReport(
    public val directory: String,
    public val role: CheckoutRole,
    public val sameRepositoryAs: String?,
    public val evidence: String,
)

/**
 * The result of `kscope info --structure`. [truncated] says that the walk stopped at the limit of directories, so that
 * more may exist; [depth] is the number of directory levels below [directory] that were inspected.
 * [ignoredDirectoriesSkipped] counts pruned directories, excluding their descendants; [ignoreRulesNotice] explains
 * why Git ignore rules could not be applied, or is null when no fallback was needed.
 */
@Serializable
public data class StructureResult(
    public val directory: String,
    public val depth: Int,
    public val conclusion: StructureConclusion,
    public val git: GitReport?,
    public val worktrees: List<WorktreeReport>,
    public val gradleBuilds: List<GradleBuildReport>,
    public val projects: List<ProjectReport>,
    public val nestedRepositories: List<NestedRepositoryReport>,
    public val directoriesVisited: Int,
    public val ignoredDirectoriesSkipped: Int,
    public val ignoreRulesNotice: String?,
    public val truncated: Boolean,
)
