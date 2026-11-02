package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiRecordComponent
import com.intellij.psi.PsiRecordHeader
import io.github.krosstack.kscope.model.DescriptorSegment
import io.github.krosstack.kscope.model.MethodDisambiguator
import io.github.krosstack.kscope.model.SymbolDescriptor

/**
 * Identity of a Java member or class computed from its syntax alone, by the same rules as [KotlinDescriptors].
 * Returns null for anonymous and local classes and for anything inside them.
 */
internal object JavaDescriptors {
    fun of(member: PsiMember): SymbolDescriptor? {
        val own = ownSegment(member)
        val enclosing = enclosingClasses(member)
        if (own == null || enclosing == null) return null
        val packageDescriptor = packageNameOf(member).split('.').filter { it.isNotEmpty() }
            .fold(SymbolDescriptor()) { descriptor, name -> descriptor.namespace(name) }
        return enclosing.fold(packageDescriptor) { descriptor, name -> descriptor.type(name) } + own
    }

    fun qualifiedName(member: PsiMember): String? {
        val enclosing = enclosingClasses(member)
        val name = if (member is PsiMethod && member.isConstructor) CONSTRUCTOR_NAME else member.name
        if (enclosing == null || name == null) return null
        return (listOf(packageNameOf(member)).filter { it.isNotEmpty() } + enclosing + name).joinToString(".")
    }

    private fun packageNameOf(member: PsiMember): String =
        (member.containingFile as? PsiJavaFile)?.packageName.orEmpty()

    private fun ownSegment(member: PsiMember): DescriptorSegment? = when (member) {
        is PsiClass -> member.name?.let { DescriptorSegment.Type(it) }
        is PsiMethod -> methodSegment(member)
        is PsiField -> DescriptorSegment.Term(member.name)
        is PsiRecordComponent -> DescriptorSegment.Term(member.name)
        else -> null
    }

    private fun methodSegment(method: PsiMethod): DescriptorSegment {
        val name = if (method.isConstructor) CONSTRUCTOR_NAME else method.name
        val parameters = javaParameterTypes(method.parameterList.parameters)
        return DescriptorSegment.Method(name, MethodDisambiguator.render(null, parameters))
    }

    private fun enclosingClasses(member: PsiMember): List<String>? {
        val ancestors = generateSequence(member.parent) { it.parent }
            .takeWhile { it !is PsiFile }
            .filterNot { it is PsiRecordHeader }
            .toList()
        val names = ancestors.map { (it as? PsiClass)?.takeUnless { named -> named is PsiAnonymousClass }?.name }
        return if (null in names) null else names.filterNotNull().reversed()
    }
}
