package io.github.krosstack.kscope.analyzer.gradle.statics.layout

import io.github.krosstack.kscope.analyzer.gradle.mapping.SourceSetNaming
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.Variant
import io.github.krosstack.kscope.model.VariantId

/** The targets, variants and source sets of a module, with the identities the drafts of its layout stand for. */
internal class ModuleParts(val moduleId: ModuleId, layout: Layout) {
    private val targetIds = layout.targets.associate { it.name to TargetId("${moduleId.value}#target/${it.name}") }
    private val variantIds = layout.variants.associate { it.name to VariantId("${moduleId.value}#variant/${it.name}") }
    private val sourceSetNames = layout.sourceSets.map { it.name }.toSet()

    val targets: List<Target> =
        layout.targets.map { Target(targetIds.getValue(it.name), moduleId, it.name, it.platform) }

    val variants: List<Variant> =
        layout.variants.map { Variant(variantIds.getValue(it.name), moduleId, it.name, it.buildType, it.flavors) }

    val sourceSets: List<SourceSet> = layout.sourceSets.map(::sourceSetOf)

    private fun sourceSetOf(draft: SourceSetDraft) = SourceSet(
        id = sourceSetId(draft.name),
        module = moduleId,
        name = draft.name,
        role = SourceSetNaming.roleOf(draft.name),
        dependsOn = draft.dependsOn.filter { it in sourceSetNames && it != draft.name }.map(::sourceSetId)
            .sortedBy { it.value },
        roots = emptyList(),
        targets = draft.targets.mapNotNull(targetIds::get).sortedBy { it.value },
        variants = draft.variants.mapNotNull(variantIds::get).sortedBy { it.value },
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = if (draft.declared) Certainty.EXACT else Certainty.HEURISTIC,
    )

    private fun sourceSetId(name: String) = SourceSetId("${moduleId.value}@$name")
}
