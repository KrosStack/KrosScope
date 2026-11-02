package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.TextRange

/** Where the symbols of one file belong: its module, its source set if it has one, and its places in the file. */
internal class SymbolAnchor(private val artifact: Artifact, private val module: Module, sourceSet: SourceSet?) {
    private val sourceSetName = sourceSet?.id?.value?.removePrefix("${module.id.value}@")
    val scope = Scope(sourceSet = sourceSet?.id)

    val path: String get() = artifact.ref.path.value

    fun idOf(descriptor: SymbolDescriptor): SymbolId =
        SymbolIdentity.workspace(module.id, sourceSetName, descriptor).toSymbolId()

    fun locationOf(range: TextRange) = SourceLocation(artifact.ref, range)
}
