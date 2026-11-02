package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Attempt
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.SyntheticMembers
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Target
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.WorkspaceIds
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/** Finds the class that a reference to a member of a compiler plugin stands for, such as `Foo.serializer()`. */
internal class KotlinSyntheticMembers(private val resolver: KotlinResolver, private val ids: WorkspaceIds) {
    fun ownerOf(name: KtNameReferenceExpression): Target.Workspace? {
        val member = name.getReferencedName().takeIf(SyntheticMembers::isPluginMemberName)
        val owner = member?.let { SyntheticMembers.ownerOf(it, receiverClassOf(name)) }
        return owner?.let(ids::of)?.let { Target.Workspace(it) }
    }

    // Without a written receiver the member is read from the class around the reference or from its companion.
    private fun receiverClassOf(name: KtNameReferenceExpression): KtClassOrObject? {
        val qualified = qualifiedExpressionOf(name)
        return if (qualified == null) name.getParentOfType<KtClassOrObject>(true) else classNamedBy(qualified)
    }

    private fun qualifiedExpressionOf(name: KtNameReferenceExpression): KtDotQualifiedExpression? {
        val call = (name.parent as? KtCallExpression)?.takeIf { it.calleeExpression === name }
        val selector: KtExpression = call ?: name
        return (selector.parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression === selector }
    }

    private fun classNamedBy(qualified: KtDotQualifiedExpression): KtClassOrObject? {
        val receiver = qualified.receiverExpression
        val last = (receiver as? KtDotQualifiedExpression)?.selectorExpression ?: receiver
        val symbols = ((last as? KtNameReferenceExpression)?.let(resolver::symbolsOf) as? Attempt.Resolved)?.symbols
        return symbols.orEmpty().firstNotNullOfOrNull { (it as? KaClassSymbol)?.psi as? KtClassOrObject }
    }
}
