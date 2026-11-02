package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspacePaths
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.Variant
import io.github.krosstack.kscope.model.VariantId

/** The entities of one Gradle project. [classpaths] are runtime data and never become facts. */
internal class MappedProject(
    val module: Module,
    val targets: List<Target>,
    val variants: List<Variant>,
    val sourceSets: List<SourceSet>,
    val classpaths: Map<SourceSetId, List<String>>,
)

/**
 * Maps one project document to a module with its targets, variants and source sets. A project that applies no plugin
 * and has no source directory, such as the aggregating root project of a build, is not a module.
 */
internal class ProjectMapper(private val buildRoot: BuildRoot, private val workspace: WorkspacePaths) {
    fun map(document: ProjectDocument): MappedProject? {
        val drafts = SourceSetDrafts(document, workspace).emitted()
        return workspace.relative(document.dir)
            ?.takeIf { document.plugins.isNotEmpty() || drafts.isNotEmpty() }
            ?.let { assemble(document, it, drafts) }
    }

    private fun assemble(document: ProjectDocument, directory: RepoPath, drafts: List<SourceSetDraft>): MappedProject {
        val moduleId = ModuleId(buildRoot.id.value + document.path)
        val targets = ProjectTargets(document, moduleId).drafts(drafts.map { it.name }.toSet())
        val variants = variantsOf(document, moduleId)
        val sourceSets = drafts.map { sourceSetOf(it, moduleId, targets, variantMembership(document, moduleId)) }
        val module = Module(
            id = moduleId,
            buildRoot = buildRoot.id,
            path = document.path,
            directory = directory,
            plugins = document.plugins.sorted(),
            traits = ModuleTraits.of(document),
            androidNamespace = document.android?.namespace,
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = Certainty.EXACT,
            attributes = ModuleAttributes.of(document),
        )
        val classpaths = ClasspathMapper.map(document, sourceSets.associate { it.name to it.id })
        return MappedProject(module, targets.map { it.target }, variants, sourceSets, classpaths)
    }

    private fun variantsOf(document: ProjectDocument, module: ModuleId): List<Variant> =
        document.android?.takeIf { it.isDsl }?.variants.orEmpty().map { variant ->
            Variant(variantId(module, variant.name), module, variant.name, variant.buildType, variant.flavors)
        }

    private fun variantMembership(document: ProjectDocument, module: ModuleId): Map<String, List<VariantId>> =
        document.android?.takeIf { it.isDsl }?.variants.orEmpty()
            .flatMap { variant ->
                val names = variant.sourceSets.main + variant.sourceSets.unitTest + variant.sourceSets.androidTest
                names.map { it to variantId(module, variant.name) }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ids) -> ids.distinct().sortedBy { it.value } }

    private fun sourceSetOf(
        draft: SourceSetDraft,
        module: ModuleId,
        targets: List<TargetDraft>,
        membership: Map<String, List<VariantId>>,
    ) = SourceSet(
        id = sourceSetId(module, draft.name),
        module = module,
        name = draft.name,
        role = SourceSetNaming.roleOf(draft.name),
        dependsOn = draft.dependsOn.map { sourceSetId(module, it) },
        roots = draft.roots,
        targets = targets.filter { draft.name in it.sourceSets }.map { it.target.id }.sortedBy(TargetId::value),
        variants = membership[draft.name].orEmpty(),
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )

    private fun sourceSetId(module: ModuleId, name: String) = SourceSetId("${module.value}@$name")

    private fun variantId(module: ModuleId, name: String) = VariantId("${module.value}#variant/$name")
}
