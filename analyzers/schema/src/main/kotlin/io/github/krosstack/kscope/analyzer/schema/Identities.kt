package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity

/** The name that symbol identities use for a source set: its id without the module. */
internal fun SourceSet.symbolName(): String = id.value.removePrefix("${module.value}@")

/** Makes the ids of the symbols of one module and source set; a null source set stands for the module itself. */
internal class IdentityFactory(private val module: ModuleId, private val sourceSetName: String?) {
    fun idOf(descriptor: SymbolDescriptor): SymbolId =
        SymbolIdentity.workspace(module, sourceSetName, descriptor).toSymbolId()
}

/** The descriptor of a dotted package or namespace path, one namespace segment per name. */
internal fun SymbolDescriptor.namespaces(path: String): SymbolDescriptor =
    path.split('.').filter { it.isNotEmpty() }.fold(this) { descriptor, name -> descriptor.namespace(name) }
