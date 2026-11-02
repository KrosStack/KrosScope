package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind

/** The identity of a resource symbol: its module, its source set and `compose/res/<type>/<name>.` */
internal object ComposeResourceSymbols {
    fun idOf(module: ModuleId, sourceSetName: String, key: ResourceKey): SymbolId {
        val descriptor = SymbolDescriptor().namespace("compose").namespace("res").namespace(key.type.id).term(key.name)
        return SymbolIdentity.workspace(module, sourceSetName, descriptor).toSymbolId()
    }

    fun kindOf(type: ResourceType) = SymbolKind(type.kindId, SymbolCategory.RESOURCE)
}

/** The name that symbol identities use for a source set: its id without the module. */
internal fun SourceSet.symbolName(): String = id.value.removePrefix("${module.value}@")
