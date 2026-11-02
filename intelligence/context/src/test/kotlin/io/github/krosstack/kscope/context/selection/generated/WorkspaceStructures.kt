package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal fun workspaceStructure(plans: List<FilePlan>): WorkspaceStructure {
    val repositories = plans.map { it.repository }.distinct()
    val modules = plans.mapNotNull { it.module }.distinct()
    val sourceSets = plans.mapNotNull { plan -> plan.module?.let { plan.repository to it } }.distinct()
    return WorkspaceStructure(
        repositories = repositories.map { Repository(RepositoryId(it), it, null, null) },
        buildRoots = repositories.map { buildRoot(it) },
        modules = modules.map(::moduleOf),
        targets = emptyList(),
        variants = emptyList(),
        sourceSets = sourceSets.map { (repository, module) -> sourceSet(repository, module) },
    )
}

private fun buildRoot(repository: String) =
    BuildRoot(BuildRootId("$repository//"), RepositoryId(repository), RepoPath(""), BuildSystem.GRADLE)

private fun moduleOf(id: ModuleId): Module {
    val name = id.value.substringAfter("//:")
    return Module(
        id = id,
        buildRoot = BuildRootId(id.value.substringBefore("//:") + "//"),
        path = ":$name",
        directory = RepoPath(name),
        plugins = emptyList(),
        traits = setOf(ModuleTrait.KOTLIN_JVM),
        androidNamespace = null,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )
}

private fun sourceSet(repository: String, module: ModuleId) = SourceSet(
    id = sourceSetOf(repository, module),
    module = module,
    name = "main",
    role = SourceSetRole.MAIN,
    dependsOn = emptyList(),
    roots = emptyList(),
    targets = emptyList(),
    variants = emptyList(),
    evidenceClass = EvidenceClass.BUILD_MODEL,
    certainty = Certainty.EXACT,
)
