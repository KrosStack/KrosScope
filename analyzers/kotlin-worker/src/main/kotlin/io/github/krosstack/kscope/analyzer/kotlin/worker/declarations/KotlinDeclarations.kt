package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias

private class Container(val id: SymbolId, val element: KtClassOrObject)

/** Extracts the declarations of Kotlin files from their syntax trees, independent of any resolution. */
internal class KotlinDeclarations(private val qualify: (KtAnnotationEntry) -> String?) {
    fun extract(file: KtFile, context: FileContext, out: MutableList<Symbol>) {
        KotlinFileWalker(context, KotlinAnnotations(qualify, file), out).walk(file)
    }
}

private class KotlinFileWalker(
    private val context: FileContext,
    private val annotations: KotlinAnnotations,
    private val out: MutableList<Symbol>,
) {
    fun walk(file: KtFile) {
        file.declarations.forEach { visit(it, null) }
    }

    private fun visit(declaration: KtDeclaration, container: Container?) {
        when (declaration) {
            is KtEnumEntry -> emit(draftOf(declaration, container, variableKind("kotlin.enum-entry")))
            is KtClassOrObject -> visitClass(declaration, container)
            is KtNamedFunction -> emit(draftOf(declaration, container, callableKind("kotlin.function")))
            is KtProperty -> emit(draftOf(declaration, container, variableKind("kotlin.property")))
            is KtTypeAlias -> emit(draftOf(declaration, container, typeKind("kotlin.typealias")))
            is KtSecondaryConstructor -> emit(draftOf(declaration, container, callableKind("kotlin.constructor")))
            else -> Unit
        }
    }

    private fun visitClass(declaration: KtClassOrObject, container: Container?) {
        val draft = draftOf(declaration, container, classKind(declaration)) ?: return
        emit(draft)
        val self = Container(context.idOf(draft.descriptor), declaration)
        declaration.primaryConstructor?.let { visitPrimaryConstructor(it, self) }
        declaration.declarations.forEach { visit(it, self) }
    }

    private fun visitPrimaryConstructor(constructor: KtPrimaryConstructor, container: Container) {
        emit(draftOf(constructor, container, callableKind("kotlin.constructor")))
        constructor.valueParameters.filter { it.hasValOrVar() }.forEach {
            emit(draftOf(it, container, variableKind("kotlin.property")))
        }
    }

    private fun classKind(declaration: KtClassOrObject): SymbolKind = when {
        declaration is KtObjectDeclaration -> typeKind("kotlin.object")
        declaration is KtClass && declaration.isInterface() -> typeKind("kotlin.interface")
        declaration is KtClass && declaration.isEnum() -> typeKind("kotlin.enum")
        declaration is KtClass && declaration.isAnnotation() -> typeKind("kotlin.annotation")
        else -> typeKind("kotlin.class")
    }

    private fun draftOf(declaration: KtDeclaration, container: Container?, kind: SymbolKind): SymbolDraft? {
        val descriptor = KotlinDescriptors.of(declaration)
        val qualifiedName = KotlinDescriptors.qualifiedName(declaration)
        if (descriptor == null || qualifiedName == null) return null
        val uses = annotations.usesOf(declaration)
        return SymbolDraft(
            kind = kind,
            name = nameOf(declaration),
            qualifiedName = qualifiedName,
            descriptor = descriptor,
            container = container?.id,
            signature = kotlinSignature(declaration),
            visibility = kotlinVisibility(declaration),
            flags = kotlinModifierFlags(declaration) + annotationFlags(uses, annotations.imports),
            declaration = declarationOffsets(declaration),
            nameRange = nameRangeOf(declaration),
            documentation = declaration.docComment?.let { firstSentence(it.text) },
            attributes = annotationAttributes(uses),
        )
    }

    private fun nameOf(declaration: KtDeclaration): String = when (declaration) {
        is KtConstructor<*> -> CONSTRUCTOR_NAME
        is KtClassOrObject -> checkNotNull(KotlinDescriptors.typeName(declaration))
        else -> checkNotNull(declaration.name)
    }

    private fun nameRangeOf(declaration: KtDeclaration): IntRange? = when (declaration) {
        is KtConstructor<*> -> (declaration.getConstructorKeyword() ?: declaration.valueParameterList)?.offsets()
        is KtObjectDeclaration -> (declaration.nameIdentifier ?: declaration.getObjectKeyword())?.offsets()
        is KtNamedDeclaration -> declaration.nameIdentifier?.offsets()
        else -> null
    }

    private fun emit(draft: SymbolDraft?) {
        if (draft != null) out += context.symbolOf(draft)
    }
}
