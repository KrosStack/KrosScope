package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.libraries

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.JavaDescriptors
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity

/** Names Java declarations of libraries and the JDK with the descriptor rules that apply to workspace code. */
internal object JavaLibraryIds {
    fun idOf(element: PsiElement): SymbolId? {
        val member = element as? PsiMember
        val descriptor = member?.let { JavaDescriptors.of(it) } ?: return null
        val origin = LibraryOrigins.of(member.containingFile?.virtualFile)
        return SymbolIdentity.library(origin.ecosystem, origin.coordinate, descriptor).toSymbolId()
    }
}
