package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import com.intellij.psi.PsiJavaCodeReferenceElement
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.SyntheticMembers
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Target
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.WorkspaceIds

/** Finds the Kotlin class that a Java reference to a member of a compiler plugin, such as `Foo.CREATOR`, stands for. */
internal class JavaSyntheticMembers(private val resolver: JavaResolver, private val ids: WorkspaceIds) {
    fun ownerOf(reference: PsiJavaCodeReferenceElement): Target.Workspace? {
        val member = reference.referenceName?.takeIf(SyntheticMembers::isPluginMemberName)
        val qualifier = reference.qualifier as? PsiJavaCodeReferenceElement
        val owner = member?.let { SyntheticMembers.ownerOf(it, qualifier?.let(resolver::kotlinClassOf)) }
        return owner?.let(ids::of)?.let { Target.Workspace(it) }
    }
}
