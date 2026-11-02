package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.model.DescriptorSegment
import io.github.krosstack.kscope.model.MethodDisambiguator
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias

/** The last descriptor segment of a Kotlin declaration: its kind of segment and, for callables, the disambiguator. */
internal object KotlinSegments {
    fun of(declaration: KtDeclaration): DescriptorSegment? = when (declaration) {
        is KtEnumEntry -> term(declaration.name)
        is KtClassOrObject -> type(KotlinDescriptors.typeName(declaration))
        is KtTypeAlias -> type(declaration.name)
        is KtNamedFunction -> method(declaration.name, functionDisambiguator(declaration))
        is KtProperty -> propertySegment(declaration)
        is KtParameter -> if (declaration.hasValOrVar()) term(declaration.name) else null
        is KtConstructor<*> -> method(CONSTRUCTOR_NAME, constructorDisambiguator(declaration))
        else -> null
    }

    private fun term(name: String?) = name?.let { DescriptorSegment.Term(it) }

    private fun type(name: String?) = name?.let { DescriptorSegment.Type(it) }

    private fun method(name: String?, disambiguator: String) = name?.let { DescriptorSegment.Method(it, disambiguator) }

    private fun functionDisambiguator(function: KtNamedFunction): String = MethodDisambiguator.render(
        function.receiverTypeReference?.let { kotlinTypeName(it) },
        kotlinParameterTypes(function.valueParameters),
    )

    private fun constructorDisambiguator(constructor: KtConstructor<*>): String =
        MethodDisambiguator.render(null, kotlinParameterTypes(constructor.valueParameters))

    private fun propertySegment(property: KtProperty): DescriptorSegment? {
        val receiver = property.receiverTypeReference ?: return term(property.name)
        return method(property.name, MethodDisambiguator.render(kotlinTypeName(receiver), emptyList()))
    }
}
