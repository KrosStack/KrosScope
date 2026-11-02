package io.github.krosstack.kscope.analyzer.compose.res

import io.github.krosstack.kscope.analyzer.compose.ComposeResourceSymbols
import io.github.krosstack.kscope.analyzer.compose.ResourceDefinition
import io.github.krosstack.kscope.analyzer.compose.ResourceKey
import io.github.krosstack.kscope.analyzer.compose.symbolName
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.SymbolId

/** A resource definition together with the module and source set whose file declares it. */
internal class OwnedDefinition(val module: Module, val sourceSet: SourceSet, val definition: ResourceDefinition) {
    val symbolId: SymbolId = ComposeResourceSymbols.idOf(module.id, sourceSet.symbolName(), definition.key)
}

/** One accessor of the resource class, such as `Res.string.app_name`; [sources] are the definitions it comes from. */
internal class ResAccessor(val key: ResourceKey, val sources: List<OwnedDefinition>) {
    val name: String get() = key.name
}

/**
 * The resource class of one module: the package and the source set whose stub declares it, whether it is public, and
 * the accessors ordered by type and name. [skippedNames] counts the resources whose name is no Kotlin name.
 */
internal data class ResPlan(
    val module: Module,
    val pkg: ResPackage,
    val host: SourceSet,
    val isPublic: Boolean,
    val accessors: List<ResAccessor>,
    val skippedNames: Int,
)

internal object ResPlanner {
    fun plan(module: Module, pkg: ResPackage, host: SourceSet, definitions: List<OwnedDefinition>): ResPlan {
        val isPublic = module.attributes[PUBLIC_ATTRIBUTE] == "true"
        val usable = definitions.filter { KotlinNames.isIdentifier(it.definition.key.name) }
        val accessors = usable.groupBy { it.definition.key }
            .toSortedMap(compareBy({ it.type.ordinal }, { it.name }))
            .map { (key, sources) -> ResAccessor(key, sources.distinctBy { it.symbolId }) }
        val skipped = definitions.map { it.definition.key }.distinct().size - accessors.size
        return ResPlan(module, pkg, host, isPublic, accessors, skipped)
    }
}

/** The source set whose stub declares the resource class: the common one of the module, else its `main`. */
internal object ResHost {
    fun of(moduleSourceSets: List<SourceSet>): SourceSet? {
        val mains = moduleSourceSets.filter { it.role == SourceSetRole.MAIN }.sortedBy { it.id.value }
        return mains.firstOrNull { it.name == "commonMain" }
            ?: mains.firstOrNull { it.name == "main" }
            ?: mains.firstOrNull { it.dependsOn.isEmpty() }
            ?: mains.firstOrNull()
    }
}
