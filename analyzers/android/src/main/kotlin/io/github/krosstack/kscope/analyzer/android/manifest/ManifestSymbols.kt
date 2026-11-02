package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind

/** The identity of a manifest symbol: its module, its source set and `android/manifest/<tag>/<name>.` */
internal object ManifestSymbols {
    const val KIND_PREFIX = "android.manifest."

    fun idOf(module: ModuleId, sourceSetName: String, tag: String, name: String): SymbolId {
        val descriptor = SymbolDescriptor().namespace("android").namespace("manifest").namespace(tag).term(name)
        return SymbolIdentity.workspace(module, sourceSetName, descriptor).toSymbolId()
    }

    fun kindOf(tag: String) = SymbolKind("$KIND_PREFIX$tag", SymbolCategory.COMPONENT)
}
