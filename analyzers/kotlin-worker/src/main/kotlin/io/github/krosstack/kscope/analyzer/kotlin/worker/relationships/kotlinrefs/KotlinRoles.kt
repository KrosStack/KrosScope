package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtInstanceExpressionWithLabel
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/** What a name in Kotlin source is doing, which decides the kind of relationship it gives rise to. */
internal sealed interface Role {
    data object Skip : Role

    data object Call : Role

    data object TypeUse : Role

    data class Supertype(val declaringIsInterface: Boolean, val hasConstructorCall: Boolean, val anonymous: Boolean) :
        Role

    data object Annotation : Role

    data object Value : Role

    data object CallableReference : Role

    data object NamedArgument : Role
}

private val incrementTokens = setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS)
private val dottedName = Regex("""[A-Za-z_]\w*(\.[A-Za-z_]\w*)*""")

/** Classifies a name reference by the syntax around it. */
internal object KotlinRoles {
    fun of(name: KtNameReferenceExpression): Role {
        val parent = name.parent
        return when {
            parent is KtInstanceExpressionWithLabel -> Role.Skip
            parent is KtValueArgumentName -> Role.NamedArgument
            parent is KtCallExpression && parent.calleeExpression === name -> Role.Call
            parent is KtUserType -> ofType(parent)
            parent is KtCallableReferenceExpression && parent.callableReference === name -> Role.CallableReference
            else -> Role.Value
        }
    }

    fun accessOf(name: KtNameReferenceExpression): Access {
        val expression = outermostExpressionOf(name)
        val parent = expression.parent
        val isWritten = when (parent) {
            is KtBinaryExpression -> parent.left === expression && parent.operationToken in KtTokens.ALL_ASSIGNMENTS
            is KtUnaryExpression -> parent.operationToken in incrementTokens
            else -> false
        }
        return if (isWritten) Access.WRITE else Access.READ
    }

    /** The dotted path that ends at [name] when it qualifies something longer, as a package prefix does. */
    fun qualifierPathOf(name: KtNameReferenceExpression): String? {
        val parent = name.parent
        val qualifier: KtElement? = when {
            parent is KtUserType -> parent.takeIf { it.parent is KtUserType }
            parent is KtDotQualifiedExpression && parent.selectorExpression === name -> parent
            else -> name
        }
        return qualifier?.takeIf(::qualifiesSomething)?.text?.takeIf { dottedName.matches(it) }
    }

    /** The name that [type] is written with, qualified as in the source, such as `lib.api.Greeter`. */
    fun typeNameOf(type: KtUserType): String =
        listOfNotNull(type.qualifier?.let(::typeNameOf), type.referencedName).joinToString(".")

    /** Whether [name] is `it`, which a lambda without a parameter list declares implicitly. */
    fun isImplicitLambdaParameter(name: KtNameReferenceExpression): Boolean {
        val lambda = name.getParentOfType<KtFunctionLiteral>(true)
        return name.getReferencedName() == "it" && lambda?.hasParameterSpecification() == false
    }

    private fun qualifiesSomething(element: KtElement): Boolean {
        val outer = element.parent
        return element is KtUserType || (outer is KtDotQualifiedExpression && outer.receiverExpression === element)
    }

    private fun ofType(type: KtUserType): Role {
        val isQualifier = type.parent is KtUserType
        val owner = type.getParentOfType<KtTypeReference>(true)?.parent?.takeUnless { isQualifier }
        return when (owner) {
            is KtSuperTypeListEntry -> supertype(owner, owner is KtSuperTypeCallEntry)
            is KtConstructorCalleeExpression -> ofConstructorCallee(owner)
            else -> Role.TypeUse
        }
    }

    private fun ofConstructorCallee(callee: KtConstructorCalleeExpression): Role = when (val owner = callee.parent) {
        is KtSuperTypeCallEntry -> supertype(owner, true)
        is KtAnnotationEntry -> Role.Annotation
        else -> Role.TypeUse
    }

    private fun supertype(entry: KtSuperTypeListEntry, hasConstructorCall: Boolean): Role {
        val declaring = entry.getParentOfType<KtClassOrObject>(true)
        val isInterface = declaring is KtClass && declaring.isInterface()
        val isAnonymous = declaring is KtObjectDeclaration && declaring.isObjectLiteral()
        return Role.Supertype(isInterface, hasConstructorCall, isAnonymous)
    }

    private fun outermostExpressionOf(name: KtExpression): KtExpression {
        var current: KtExpression = name
        while (true) {
            val parent = current.parent
            current = when {
                parent is KtQualifiedExpression && parent.selectorExpression === current -> parent
                parent is KtParenthesizedExpression -> parent
                else -> return current
            }
        }
    }
}
