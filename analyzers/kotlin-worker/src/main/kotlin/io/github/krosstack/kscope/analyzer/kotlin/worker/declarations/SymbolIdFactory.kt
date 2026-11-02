package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.FileOwner
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.FileOwners
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import org.jetbrains.kotlin.psi.KtDeclaration

internal fun FileOwner.idOf(descriptor: SymbolDescriptor): SymbolId =
    SymbolIdentity.workspace(module, sourceSetName, descriptor).toSymbolId()

/**
 * The id of a workspace declaration, computed from the declaration syntax and the source set of its file. A resolving
 * pass uses it to name the target of a reference: the result equals the id that the declaration pass emitted for that
 * declaration. A file that is shared by several modules has one owner per module, so pass the [moduleName] of the
 * module the declaration was seen from. Null for local declarations and for files outside the request.
 */
internal class SymbolIdFactory(private val owners: FileOwners) {
    fun idOf(declaration: KtDeclaration, moduleName: String? = null): SymbolId? =
        idOf(declaration, moduleName, KotlinDescriptors.of(declaration))

    fun idOf(member: PsiMember, moduleName: String? = null): SymbolId? =
        idOf(member, moduleName, JavaDescriptors.of(member))

    fun artifactOf(file: PsiFile, moduleName: String? = null): ArtifactRef? =
        file.virtualFile?.path?.let { owners.of(it, moduleName) }?.artifact

    private fun idOf(element: PsiElement, moduleName: String?, descriptor: SymbolDescriptor?): SymbolId? {
        val path = element.containingFile?.virtualFile?.path ?: return null
        val owner = owners.of(path, moduleName)
        return if (owner == null || descriptor == null) null else owner.idOf(descriptor)
    }
}
