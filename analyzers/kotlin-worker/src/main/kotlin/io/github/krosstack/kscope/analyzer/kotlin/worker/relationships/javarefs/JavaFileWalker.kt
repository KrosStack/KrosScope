package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiRecursiveElementVisitor
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.WorkspaceIds
import io.github.krosstack.kscope.model.NodeRef

/**
 * Walks the syntax tree of one Java file and hands every reference to the code that resolves it, together with the
 * innermost declared symbol around it. Members of anonymous and local classes are not symbols, so what they refer to
 * is attributed to the symbol that contains them.
 */
internal class JavaFileWalker(private val ids: WorkspaceIds, private val references: JavaReferences) {
    fun walk(file: PsiJavaFile, fileRef: NodeRef) {
        file.accept(Visitor(fileRef))
    }

    private inner class Visitor(private val fileRef: NodeRef) : PsiRecursiveElementVisitor() {
        private var enclosing: NodeRef = fileRef

        override fun visitElement(element: PsiElement) {
            when (element) {
                is PsiComment, is PsiPackageStatement -> return
                is PsiImportStatementBase -> return references.import(element, fileRef)
                else -> Unit
            }
            val outer = enclosing
            enclosing = declaration(element) ?: outer
            report(element)
            super.visitElement(element)
            enclosing = outer
        }

        private fun declaration(element: PsiElement): NodeRef? {
            val id = (element as? PsiMember)?.let { ids.scopeOf(it) } ?: return null
            val self = NodeRef.OfSymbol(id)
            if (element is PsiMethod) references.overrides(element, self)
            return self
        }

        private fun report(element: PsiElement) {
            when (element) {
                is PsiJavaCodeReferenceElement -> references.reference(element, enclosing)
                is PsiNewExpression -> references.construction(element, enclosing)
                is PsiMethod -> references.overridesOfAnonymous(element, enclosing)
                else -> Unit
            }
        }
    }
}
