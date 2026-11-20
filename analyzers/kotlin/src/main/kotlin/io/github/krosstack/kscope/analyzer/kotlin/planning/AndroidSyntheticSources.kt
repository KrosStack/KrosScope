package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Variant
import io.github.krosstack.kscope.model.sourceSetPrecedence
import io.github.krosstack.kscope.spi.SyntheticSource

internal class AndroidSyntheticSources(private val input: PlanInput) {
    fun layoutOf(layout: ModuleLayout): ModuleLayout {
        val groups = layout.groups.map(::contextOf)
        return ModuleLayout(groups, groups.firstOrNull { it.name == layout.defaultGroup?.name })
    }

    fun sourcesOf(
        group: SourceSetGroup,
        sources: Map<SourceSetId, List<SyntheticSource>>,
    ): Map<SourceSetId, List<SyntheticSource>> {
        val variant = variantOf(group)
        if (group.platform != Platform.ANDROID || variant == null) return sources
        val byName = group.members.associateBy { it.name }
        val ordered = variant.sourceSetPrecedence().mapNotNull(byName::get) + group.members.sortedBy { it.id.value }
        return ordered.flatMap { sources[it.id].orEmpty() }.distinctBy { it.generator to it.virtualPath }
            .groupBy { it.sourceSet }
    }

    private fun contextOf(group: SourceSetGroup): SourceSetGroup {
        val files = input.syntheticSources.filter { source ->
            input.model.sourceSets.any { it.id == source.sourceSet && it.module == group.module }
        }
        val repeated = files.groupBy { it.generator to it.virtualPath }.values.any { sources ->
            sources.map { it.sourceSet }.distinct().size > 1
        }
        val variant = variantOf(group)
        val usable = group.platform == Platform.ANDROID && group.isMainSide && repeated
        if (!usable || variant == null) return group
        val members = input.model.sourceSets.filter {
            it.module == group.module && it.role == SourceSetRole.MAIN && variant.id in it.variants
        }.sortedBy { it.id.value }
        return SourceSetGroup(group.name, group.module, members, group.platform, group.ids)
    }

    private fun variantOf(group: SourceSetGroup): Variant? = SourceSetGrouping.defaultVariantOf(
        input.model.variants.filter { variant ->
            variant.module == group.module && group.members.all { variant.id in it.variants }
        },
    )
}
