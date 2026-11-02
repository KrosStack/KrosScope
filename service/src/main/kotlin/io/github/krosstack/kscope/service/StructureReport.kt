package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.engine.structure.BuildFinding
import io.github.krosstack.kscope.engine.structure.CheckoutKind
import io.github.krosstack.kscope.engine.structure.GitCheckout
import io.github.krosstack.kscope.engine.structure.ProjectFinding
import io.github.krosstack.kscope.engine.structure.StructureFindings
import io.github.krosstack.kscope.engine.structure.StructureScanner
import io.github.krosstack.kscope.service.structure.StructureConclusions
import java.nio.file.Path

/** The default number of directory levels below the workspace that `info --structure` inspects. */
public const val DEFAULT_STRUCTURE_DEPTH: Int = StructureScanner.DEFAULT_DEPTH

/**
 * Describes what the directory [root] is: its Git checkout, the other worktrees of the repository, Gradle builds, other
 * project markers and nested repositories. It needs no index, changes nothing and runs nothing from the directory.
 */
public fun KrosScope.Companion.describeStructure(root: Path, depth: Int = DEFAULT_STRUCTURE_DEPTH): StructureResult =
    StructureReport.of(StructureScanner(depth).scan(root), depth)

internal object StructureReport {
    fun of(findings: StructureFindings, depth: Int): StructureResult {
        val builds = builds(findings.builds)
        val nested = nestedRepositories(findings)
        return StructureResult(
            directory = ".",
            depth = depth,
            conclusion = StructureConclusions.of(findings, builds, nested),
            git = findings.checkout?.let(::gitReport),
            worktrees = findings.worktrees.map { WorktreeReport(it.directory, it.exists, it.evidence) },
            gradleBuilds = builds,
            projects = findings.projects.map(::projectReport),
            nestedRepositories = nested,
            directoriesVisited = findings.directoriesVisited,
            ignoredDirectoriesSkipped = findings.ignoredDirectoriesSkipped,
            ignoreRulesNotice = findings.ignoreRulesNotice,
            truncated = findings.truncated,
        )
    }

    private fun gitReport(checkout: GitCheckout) = GitReport(
        directory = checkout.directory,
        role = checkout.kind.toRole(),
        linkedWorktree = checkout.kind == CheckoutKind.LINKED_WORKTREE,
        evidence = checkout.evidence,
    )

    private fun projectReport(project: ProjectFinding) = ProjectReport(
        directory = project.directory,
        ecosystem = project.ecosystem,
        markerFile = project.markerFile,
        evidence = "${project.markerFile} in ${project.directory}",
    )

    private fun builds(found: List<BuildFinding>): List<GradleBuildReport> = found.map { build ->
        val others = found.filter { it.directory != build.directory }
        val includer = others.firstOrNull { build.directory in it.includedBuilds }
        val enclosing = others.filter { encloses(it.directory, build.directory) }.maxByOrNull { it.directory.length }
        val role = when {
            includer != null -> BuildRole.INCLUDED
            enclosing != null -> BuildRole.NESTED_INDEPENDENT
            else -> BuildRole.TOP_LEVEL
        }
        GradleBuildReport(
            directory = build.directory,
            settingsFile = build.settingsFile,
            role = role,
            includedBy = includer?.directory,
            includedBuilds = build.includedBuilds,
            evidence = evidence(build, role, includer ?: enclosing),
        )
    }

    private fun evidence(build: BuildFinding, role: BuildRole, related: BuildFinding?): String {
        val own = "${build.settingsFile} in ${build.directory}"
        val other = related?.let { "${it.settingsFile} in ${it.directory}" }
        return when (role) {
            BuildRole.INCLUDED -> "$own; $other includes it with includeBuild"
            BuildRole.NESTED_INDEPENDENT -> "$own, inside the build of $other, which does not include it"
            BuildRole.TOP_LEVEL -> own
        }
    }

    private fun encloses(outer: String, inner: String): Boolean = outer == "." || inner.startsWith("$outer/")

    private fun nestedRepositories(findings: StructureFindings): List<NestedRepositoryReport> {
        val enclosing = findings.checkout
        val owners = findings.repositories.groupBy { it.checkout.repositoryId }.mapValues { (id, group) ->
            if (enclosing != null && enclosing.repositoryId == id) {
                enclosing.directory
            } else {
                (group.firstOrNull { it.checkout.kind == CheckoutKind.REPOSITORY } ?: group.first()).directory
            }
        }
        return findings.repositories.map { repository ->
            val owner = owners.getValue(repository.checkout.repositoryId)
            NestedRepositoryReport(
                directory = repository.directory,
                role = repository.checkout.kind.toRole(),
                sameRepositoryAs = owner.takeIf { it != repository.directory },
                evidence = repository.checkout.evidence,
            )
        }
    }
}

private fun CheckoutKind.toRole(): CheckoutRole = when (this) {
    CheckoutKind.REPOSITORY -> CheckoutRole.REPOSITORY
    CheckoutKind.LINKED_WORKTREE -> CheckoutRole.LINKED_WORKTREE
    CheckoutKind.SUBMODULE -> CheckoutRole.SUBMODULE
}
