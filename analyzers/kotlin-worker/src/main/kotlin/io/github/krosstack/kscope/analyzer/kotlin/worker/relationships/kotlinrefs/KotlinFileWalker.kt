package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.WorkspaceIds
import io.github.krosstack.kscope.model.NodeRef
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective

private data class Pending(val element: PsiElement, val enclosing: NodeRef)

/**
 * Walks the syntax tree of one Kotlin file and hands every place that can hold a reference to the code that resolves
 * it, together with the innermost declared symbol around it. The walk keeps its own stack, so deeply nested
 * expressions cannot overflow the thread's.
 */
internal class KotlinFileWalker(
    private val ids: WorkspaceIds,
    private val references: KotlinReferences,
    private val callLikes: KotlinCallLikes,
    private val links: KotlinDeclarationLinks,
) {
    fun walk(file: KtFile, fileRef: NodeRef) {
        val pending = ArrayDeque<Pending>()
        pending.addLast(Pending(file, fileRef))
        while (pending.isNotEmpty()) {
            val (element, enclosing) = pending.removeLast()
            val inner = visit(element, enclosing, fileRef) ?: continue
            element.children.reversed().forEach { pending.addLast(Pending(it, inner)) }
        }
    }

    private fun visit(element: PsiElement, enclosing: NodeRef, fileRef: NodeRef): NodeRef? = when (element) {
        is KtImportDirective -> null.also { references.import(element, fileRef) }
        is KtPackageDirective, is PsiComment -> null
        else -> descend(element, enclosing)
    }

    private fun descend(element: PsiElement, enclosing: NodeRef): NodeRef {
        if (element is KtNameReferenceExpression) {
            references.name(element, enclosing)
        } else if (!callLikes.visit(element, enclosing) && element is KtDeclaration) {
            return declaration(element, enclosing)
        }
        return enclosing
    }

    private fun declaration(declaration: KtDeclaration, enclosing: NodeRef): NodeRef {
        val id = ids.scopeOf(declaration)
        if (id == null) {
            links.linkMemberOfObjectExpression(declaration, enclosing)
            return enclosing
        }
        val self = NodeRef.OfSymbol(id)
        links.link(declaration, self)
        return self
    }
}
