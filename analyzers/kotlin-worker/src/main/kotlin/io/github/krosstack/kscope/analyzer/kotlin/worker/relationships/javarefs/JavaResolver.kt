package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPolyVariantReference
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Attempt
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * Asks the Java front end what a reference stands for. A failure inside it is counted and the reference is left out,
 * so one odd construct cannot take the rest of the file with it.
 */
internal class JavaResolver {
    var failures = 0
        private set

    /** Several valid results are ambiguous unless [severalAreFine], as with the members a static import names. */
    fun attemptOf(reference: PsiPolyVariantReference, severalAreFine: Boolean = false): Attempt<PsiElement> {
        val attempt = guarded {
            val results = reference.multiResolve(false)
            val valid = results.filter { it.isValidResult }.mapNotNull { it.element }
            val isAccepted = valid.size == 1 || (valid.isNotEmpty() && severalAreFine)
            when {
                isAccepted -> Attempt.Resolved(valid)
                valid.isNotEmpty() -> Attempt.Erroneous(valid)
                else -> Attempt.Erroneous(results.mapNotNull { it.element })
            }
        }
        return attempt ?: Attempt.Unavailable
    }

    /** The Kotlin class that [reference] names, directly or through its `Companion` or `INSTANCE` field. */
    fun kotlinClassOf(reference: PsiJavaCodeReferenceElement): KtClassOrObject? {
        val resolved = guarded { listOfNotNull(reference.resolve()) }?.firstOrNull()
        val holder = if (resolved is PsiField) resolved.containingClass else resolved
        return (holder as? KtLightElement<*, *>)?.kotlinOrigin as? KtClassOrObject
    }

    fun superMethodsOf(method: PsiMethod): Attempt<PsiElement> =
        guarded { Attempt.Resolved(method.findSuperMethods().toList()) } ?: Attempt.Unavailable

    fun constructorOf(expression: PsiNewExpression, type: PsiJavaCodeReferenceElement): Constructor = guarded {
        val result = expression.resolveMethodGenerics()
        val constructor = result.element
        when {
            constructor != null && result.isValidResult -> Constructor.Explicit(Attempt.Resolved(listOf(constructor)))
            constructor != null -> Constructor.Explicit(Attempt.Erroneous(listOf(constructor)))
            else -> withoutConstructor(type)
        }
    } ?: Constructor.None

    private fun withoutConstructor(type: PsiJavaCodeReferenceElement): Constructor {
        val declared = type.resolve() as? PsiClass
        return when {
            declared == null -> Constructor.Explicit(Attempt.Erroneous(emptyList()))
            declared.isInterface -> Constructor.None
            declared.constructors.isEmpty() -> Constructor.Implicit(declared)
            else -> Constructor.Explicit(Attempt.Erroneous(declared.constructors.toList()))
        }
    }

    private fun <T : Any> guarded(block: () -> T): T? = runCatching(block).getOrElse {
        if (it is OutOfMemoryError) throw it
        failures++
        null
    }
}

/** How a `new` expression picks its constructor. */
internal sealed interface Constructor {
    data class Explicit(val attempt: Attempt<PsiElement>) : Constructor

    /** The class declares no constructor, so the call goes to the default one the language provides. */
    data class Implicit(val declared: PsiClass) : Constructor

    data object None : Constructor
}
