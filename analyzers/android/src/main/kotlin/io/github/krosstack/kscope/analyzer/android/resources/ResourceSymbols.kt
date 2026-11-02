package io.github.krosstack.kscope.analyzer.android.resources

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind

/** The identity of a resource symbol: its module, its source set and `android/res/<type>/<name>.` */
internal object ResourceSymbols {
    const val NAMESPACE = "android.resource"
    const val KIND_PREFIX = "android.resource."

    fun idOf(module: ModuleId, sourceSetName: String, key: ResourceKey): SymbolId {
        val descriptor = SymbolDescriptor().namespace("android").namespace("res").namespace(key.type).term(key.name)
        return SymbolIdentity.workspace(module, sourceSetName, descriptor).toSymbolId()
    }

    fun kindOf(type: String) = SymbolKind("$KIND_PREFIX$type", SymbolCategory.RESOURCE)
}

/** The name that symbol identities use for a source set: its id without the module. */
internal fun SourceSet.symbolName(): String = id.value.removePrefix("${module.value}@")
