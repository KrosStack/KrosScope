package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import com.intellij.psi.PsiElement
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.offsets
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.ReferenceReporter
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.ReferenceSite
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Shape
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration

private val anonymousOverrideShape = Shape.of(RelationKind.OVERRIDES, "anonymous", allowsLibrary = true)

/** Reports what a declaration says about other declarations: the members it overrides and the `expect` it fulfils. */
internal class KotlinDeclarationLinks(
    private val resolver: KotlinResolver,
    private val targets: KotlinTargets,
    private val reporter: ReferenceReporter,
) {
    fun link(declaration: KtDeclaration, self: NodeRef) {
        val site = ReferenceSite(self, siteOf(declaration).offsets(), declaration.name.orEmpty())
        if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            val overridden = resolver.overriddenBy(declaration)
            reporter.report(site, Shape.override, overridden) { targets.map(it, Access.NONE, true) }
        }
        if (declaration.hasModifier(KtTokens.ACTUAL_KEYWORD)) {
            val expects = resolver.expectsOf(declaration)
            reporter.report(site, Shape.actualizes, expects) { targets.map(it, Access.NONE, false) }
        }
    }

    /**
     * The members of an object expression are not symbols, so what they override is attributed to the symbol around
     * the object, as the supertype of the object is.
     */
    fun linkMemberOfObjectExpression(declaration: KtDeclaration, enclosing: NodeRef) {
        if (declaration.isMemberOfObjectExpression() && declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            val site = ReferenceSite(enclosing, siteOf(declaration).offsets(), declaration.name.orEmpty())
            val overridden = resolver.overriddenBy(declaration)
            reporter.report(site, anonymousOverrideShape, overridden) { targets.map(it, Access.NONE, true) }
        }
    }

    private fun siteOf(declaration: KtDeclaration): PsiElement = when (declaration) {
        is KtConstructor<*> -> declaration.getConstructorKeyword() ?: declaration.valueParameterList ?: declaration
        is KtNamedDeclaration -> declaration.nameIdentifier ?: declaration
        else -> declaration
    }
}

private fun KtDeclaration.isMemberOfObjectExpression(): Boolean {
    val owner = (parent as? KtClassBody)?.parent
    return owner is KtObjectDeclaration && owner.isObjectLiteral()
}
