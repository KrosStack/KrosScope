package io.github.krosstack.kscope.service.structure

import io.github.krosstack.kscope.engine.structure.CheckoutKind
import io.github.krosstack.kscope.engine.structure.GitCheckout
import io.github.krosstack.kscope.engine.structure.ProjectFinding
import io.github.krosstack.kscope.engine.structure.RepositoryFinding
import io.github.krosstack.kscope.engine.structure.StructureFindings
import io.github.krosstack.kscope.service.BuildRole
import io.github.krosstack.kscope.service.CheckoutRole
import io.github.krosstack.kscope.service.GradleBuildReport
import io.github.krosstack.kscope.service.NestedRepositoryReport
import io.github.krosstack.kscope.service.StructureConclusion
import io.github.krosstack.kscope.service.StructureKind

internal object StructureConclusions {
    fun of(
        findings: StructureFindings,
        builds: List<GradleBuildReport>,
        nested: List<NestedRepositoryReport>,
    ): StructureConclusion {
        val repositories = distinctRepositories(findings.checkout, findings.repositories)
        val independent = builds.filter { it.role != BuildRole.INCLUDED }
        val units = independent.size + findings.projects.size
        return when {
            repositories.size > 1 -> several(repositories, nested, findings.checkout != null)

            units > 1 -> severalBuilds(independent, findings.projects, repositories.isNotEmpty())

            units == 1 -> single(independent, findings.projects)

            else -> StructureConclusion(
                StructureKind.NONE,
                "No Gradle build or other project marker was found.",
                emptyList(),
            )
        }
    }

    private fun distinctRepositories(checkout: GitCheckout?, nested: List<RepositoryFinding>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        checkout?.let { result[it.repositoryId] = it.directory }
        nested.filter { it.checkout.kind != CheckoutKind.SUBMODULE }
            .forEach { result.putIfAbsent(it.checkout.repositoryId, it.directory) }
        return result
    }

    private fun several(
        repositories: Map<String, String>,
        nested: List<NestedRepositoryReport>,
        insideCheckout: Boolean,
    ): StructureConclusion {
        val checkouts = nested.count { it.role != CheckoutRole.SUBMODULE } + if (insideCheckout) 1 else 0
        val repeated = checkouts - repositories.size
        val verb = if (repeated == 1) "is" else "are"
        val more = noun(repeated, "more checkout")
        val note = if (repeated > 0) "; $more of a counted repository $verb not counted" else ""
        return StructureConclusion(
            StructureKind.SEVERAL_REPOSITORIES,
            "A directory holding ${repositories.size} repositories$note.",
            repositories.values.map { "repository checked out in $it" },
        )
    }

    private fun severalBuilds(
        independent: List<GradleBuildReport>,
        projects: List<ProjectFinding>,
        inRepository: Boolean,
    ): StructureConclusion {
        val parts = buildList {
            if (independent.isNotEmpty()) add(noun(independent.size, "Gradle build"))
            projects.groupBy { it.ecosystem }.toSortedMap().forEach { (ecosystem, found) ->
                add(noun(found.size, "$ecosystem project"))
            }
        }
        val owner = if (inRepository) "A repository" else "A directory"
        return StructureConclusion(
            StructureKind.SEVERAL_BUILDS,
            "$owner with several builds or projects: ${parts.joinToString(", ")}.",
            independent.map { it.evidence } + projects.map { "${it.markerFile} in ${it.directory}" },
        )
    }

    private fun single(independent: List<GradleBuildReport>, projects: List<ProjectFinding>): StructureConclusion {
        val build = independent.firstOrNull()
        val project = projects.firstOrNull()
        val (what, evidence) = if (build != null) {
            "Gradle build" to build.evidence
        } else {
            "${project?.ecosystem} project" to "${project?.markerFile} in ${project?.directory}"
        }
        return StructureConclusion(StructureKind.SINGLE_BUILD, "A single $what.", listOf(evidence))
    }

    private fun noun(count: Int, singular: String): String = "$count $singular${if (count == 1) "" else "s"}"
}
