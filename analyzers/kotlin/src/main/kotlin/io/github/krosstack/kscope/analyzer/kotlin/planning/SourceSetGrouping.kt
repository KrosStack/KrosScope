package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.Variant

/** The source sets that are analysed together as one compilation-like module. */
internal class SourceSetGroup(
    val name: String,
    val module: ModuleId,
    val members: List<SourceSet>,
    val platform: Platform,
) {
    val isMainSide: Boolean = members.all { it.role == SourceSetRole.MAIN }

    val ids: Set<SourceSetId> = members.map { it.id }.toSet()

    fun contains(sourceSetName: String): Boolean = members.any { it.name == sourceSetName }
}

/** A source set's platform follows from its targets: one platform is that platform, several are common. */
internal class PlatformResolver(targets: List<Target>) {
    private val platformByTarget: Map<TargetId, Platform> = targets.associate { it.id to it.platform }

    fun platformOf(sourceSets: List<SourceSet>): Platform {
        val platforms = sourceSets.flatMap { it.targets }.mapNotNull(platformByTarget::get).toSet()
        return when (platforms.size) {
            0 -> Platform.JVM
            1 -> platforms.single()
            else -> Platform.COMMON
        }
    }
}

internal object SourceSetGrouping {
    private const val DEBUG = "debug"

    /**
     * The default variant is the alphabetically first one built as `debug`, else the first. Its main source sets
     * compile together; every other source set is a module of its own.
     */
    fun layoutOf(sourceSets: List<SourceSet>, variants: List<Variant>, platforms: PlatformResolver): ModuleLayout {
        val default = defaultVariantOf(variants)
        val members = default?.let { variant ->
            sourceSets.filter { it.role == SourceSetRole.MAIN && variant.id in it.variants }
        }.orEmpty()
        val defaultGroup = default?.takeIf { members.isNotEmpty() }?.let { variant ->
            group(nameOf(variant, members), members, platforms)
        }
        val others = sourceSets.filter { it !in members }.map { group(it.id.value, listOf(it), platforms) }
        return ModuleLayout(listOfNotNull(defaultGroup) + others, defaultGroup)
    }

    private fun defaultVariantOf(variants: List<Variant>): Variant? {
        val sorted = variants.sortedBy { it.name }
        return sorted.firstOrNull { it.buildType == DEBUG } ?: sorted.firstOrNull()
    }

    private fun nameOf(variant: Variant, members: List<SourceSet>): String =
        if (members.size == 1) members.single().id.value else variant.id.value

    private fun group(name: String, members: List<SourceSet>, platforms: PlatformResolver) =
        SourceSetGroup(name, members.first().module, members.sortedBy { it.id.value }, platforms.platformOf(members))
}
