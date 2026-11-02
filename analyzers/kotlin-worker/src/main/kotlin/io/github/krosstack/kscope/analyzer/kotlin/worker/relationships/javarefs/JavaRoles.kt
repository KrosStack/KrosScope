package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.util.PsiUtil
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access

/** What a reference in Java source is doing, which decides the kind of relationship it gives rise to. */
internal sealed interface JavaRole {
    data object Skip : JavaRole

    data object Call : JavaRole

    data object TypeUse : JavaRole

    data class Supertype(val declaringIsInterface: Boolean, val extendsClass: Boolean, val anonymous: Boolean) :
        JavaRole

    data object Annotation : JavaRole

    data object Value : JavaRole

    data object MethodReference : JavaRole
}

/** Classifies a Java reference by the syntax around it. */
internal object JavaRoles {
    fun of(reference: PsiJavaCodeReferenceElement): JavaRole {
        val parent = reference.parent
        return when {
            reference is PsiMethodReferenceExpression -> JavaRole.MethodReference
            parent is PsiImportStatementBase || parent is PsiPackageStatement -> JavaRole.Skip
            parent is PsiNewExpression -> JavaRole.Skip
            parent is PsiAnnotation -> JavaRole.Annotation
            parent is PsiMethodCallExpression -> JavaRole.Call
            else -> ofContext(parent)
        }
    }

    /** The name a class is written with, qualified as in the source; any other reference is hinted by its last name. */
    fun hintOf(reference: PsiJavaCodeReferenceElement, role: JavaRole): String {
        val isClassName = role == JavaRole.TypeUse || role is JavaRole.Supertype || role == JavaRole.Annotation
        return if (isClassName) classNameOf(reference) else reference.referenceName.orEmpty()
    }

    private fun classNameOf(reference: PsiJavaCodeReferenceElement): String {
        val qualifier = reference.qualifier as? PsiJavaCodeReferenceElement
        val name = reference.referenceName.orEmpty()
        return if (qualifier == null) name else "${classNameOf(qualifier)}.$name"
    }

    fun accessOf(reference: PsiReferenceExpression): Access =
        if (PsiUtil.isAccessedForWriting(reference)) Access.WRITE else Access.READ

    /** Whether [reference] is the leading part of a longer qualified name, which may be a package. */
    fun isQualifier(reference: PsiJavaCodeReferenceElement): Boolean =
        (reference.parent as? PsiJavaCodeReferenceElement)?.qualifier === reference

    private fun ofContext(parent: Any?): JavaRole = when (parent) {
        is PsiReferenceList -> ofReferenceList(parent)
        is PsiAnonymousClass -> JavaRole.Supertype(parent.isInterface, true, true)
        is PsiTypeElement -> if (parent.parent is PsiClassObjectAccessExpression) JavaRole.Value else JavaRole.TypeUse
        is PsiReferenceExpression -> JavaRole.Value
        is PsiJavaCodeReferenceElement -> JavaRole.TypeUse
        else -> JavaRole.Value
    }

    private fun ofReferenceList(list: PsiReferenceList): JavaRole {
        val isInterface = (list.parent as? PsiClass)?.isInterface == true
        return when (list.role) {
            PsiReferenceList.Role.EXTENDS_LIST -> JavaRole.Supertype(isInterface, true, false)
            PsiReferenceList.Role.IMPLEMENTS_LIST -> JavaRole.Supertype(isInterface, false, false)
            else -> JavaRole.TypeUse
        }
    }
}
