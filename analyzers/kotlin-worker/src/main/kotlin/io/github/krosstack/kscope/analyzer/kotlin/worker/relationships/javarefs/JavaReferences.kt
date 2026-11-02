package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.offsets
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Attempt
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.ReferenceReporter
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.ReferenceSite
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Shape
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Target
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind

private val annotationShape = Shape.of(RelationKind.REFERENCES, "annotation")
private val methodReferenceShape = Shape.of(RelationKind.REFERENCES, "callable-reference")
private val anonymousOverrideShape = Shape.of(RelationKind.OVERRIDES, "anonymous", allowsLibrary = true)
private val implicitConstructorShape = Shape.of(RelationKind.CALLS, "constructor")

/** Reports the references that Java names and expressions make, and the methods a method overrides. */
internal class JavaReferences(
    private val resolver: JavaResolver,
    private val targets: JavaTargets,
    private val reporter: ReferenceReporter,
    private val syntheticMembers: JavaSyntheticMembers,
) {
    fun reference(reference: PsiJavaCodeReferenceElement, from: NodeRef) {
        val role = JavaRoles.of(reference)
        val shape = shapeOf(role) ?: return
        val access = accessOf(reference, role)
        val site = ReferenceSite(from, siteOf(reference).offsets(), JavaRoles.hintOf(reference, role))
        val attempt = attemptOf(reference)
        val pluginOwner = if (attempt is Attempt.Erroneous) syntheticMembers.ownerOf(reference) else null
        if (pluginOwner != null) {
            reporter.reportSyntheticMember(site, shape, pluginOwner)
        } else {
            reporter.report(site, shape, attempt) { targets.map(it, access, shape.allowsLibrary) }
        }
    }

    fun import(statement: PsiImportStatementBase, fileRef: NodeRef) {
        val reference = statement.importReference ?: return
        val site = ReferenceSite(fileRef, reference.offsets(), reference.qualifiedName ?: reference.text)
        val attempt = resolver.attemptOf(reference, severalAreFine = true)
        reporter.report(site, Shape.imports, attempt) { targets.map(it, Access.NONE, false) }
    }

    fun construction(expression: PsiNewExpression, from: NodeRef) {
        val type = expression.classOrAnonymousClassReference ?: return
        val site = ReferenceSite(from, siteOf(type).offsets(), JavaRoles.hintOf(type, JavaRole.TypeUse))
        when (val constructor = resolver.constructorOf(expression, type)) {
            is Constructor.Explicit -> reporter.report(site, Shape.call, constructor.attempt, ::map)

            is Constructor.Implicit -> {
                val attempt = Attempt.Resolved(listOf<PsiElement>(constructor.declared))
                reporter.report(site, implicitConstructorShape, attempt, ::map)
            }

            Constructor.None -> Unit
        }
    }

    fun overrides(method: PsiMethod, self: NodeRef, shape: Shape = Shape.override) {
        if (method.isConstructor || method.hasModifierProperty(PsiModifier.STATIC)) return
        val site = ReferenceSite(self, (method.nameIdentifier ?: method).offsets(), method.name)
        reporter.report(site, shape, resolver.superMethodsOf(method)) { targets.map(it, Access.NONE, true) }
    }

    /** The methods of an anonymous class are not symbols, so what they override belongs to the symbol around it. */
    fun overridesOfAnonymous(method: PsiMethod, enclosing: NodeRef) {
        if (method.containingClass is PsiAnonymousClass) overrides(method, enclosing, anonymousOverrideShape)
    }

    private fun map(element: PsiElement): Target? = targets.map(element, Access.NONE, false)

    private fun shapeOf(role: JavaRole): Shape? = when (role) {
        JavaRole.Skip -> null
        JavaRole.Call -> Shape.call
        JavaRole.TypeUse -> Shape.typeUse
        is JavaRole.Supertype -> Shape.supertype(role.declaringIsInterface, role.extendsClass, role.anonymous)
        JavaRole.Annotation -> annotationShape
        JavaRole.Value -> Shape.reference
        JavaRole.MethodReference -> methodReferenceShape
    }

    private fun accessOf(reference: PsiJavaCodeReferenceElement, role: JavaRole): Access {
        val isValue = role == JavaRole.Value && reference is PsiReferenceExpression
        return if (isValue) JavaRoles.accessOf(reference) else Access.NONE
    }

    private fun siteOf(reference: PsiJavaCodeReferenceElement): PsiElement = reference.referenceNameElement ?: reference

    // The leading part of a qualified name may be a package, which no front end resolves to a declaration.
    private fun attemptOf(reference: PsiJavaCodeReferenceElement): Attempt<PsiElement> {
        val attempt = resolver.attemptOf(reference)
        val isUnresolved = attempt is Attempt.Erroneous && attempt.candidates.isEmpty()
        return if (isUnresolved && JavaRoles.isQualifier(reference)) Attempt.Unavailable else attempt
    }
}
