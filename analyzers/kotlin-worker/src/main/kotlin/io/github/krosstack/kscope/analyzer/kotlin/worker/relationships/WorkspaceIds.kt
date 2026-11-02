package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.SymbolIdFactory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.SymbolId
import org.jetbrains.kotlin.psi.KtDeclaration

/**
 * Names workspace declarations for the module of the file being analysed. A declaration is looked up in the module
 * that the compiler says it belongs to, then in whichever module claims its file: the file of a target may be shared
 * by several modules, and any of them names it the same way.
 */
internal class WorkspaceIds(
    private val factory: SymbolIdFactory,
    private val currentModule: String,
    private val collisions: IdentityCollisions,
) {
    fun of(declaration: KtDeclaration, moduleHint: String? = null): SymbolId? =
        factory.idOf(declaration, moduleHint ?: currentModule) ?: factory.idOf(declaration, null)

    fun of(member: PsiMember, moduleHint: String? = null): SymbolId? =
        factory.idOf(member, moduleHint ?: currentModule) ?: factory.idOf(member, null)

    /** The symbol that references inside [declaration] are attributed to; none if another declaration has its id. */
    fun scopeOf(declaration: KtDeclaration): SymbolId? = of(declaration)?.takeUnless(collisions::isShared)

    fun scopeOf(member: PsiMember): SymbolId? = of(member)?.takeUnless(collisions::isShared)

    fun artifactOf(file: PsiFile, moduleHint: String? = null): ArtifactRef? =
        factory.artifactOf(file, moduleHint ?: currentModule) ?: factory.artifactOf(file, null)
}
