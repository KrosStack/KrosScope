package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNameIdentifierOwner
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind

private class JavaContainer(val id: SymbolId)

/** Extracts the declarations of Java files from their syntax trees, independent of any resolution. */
internal class JavaDeclarations {
    fun extract(file: PsiJavaFile, context: FileContext, out: MutableList<Symbol>) {
        JavaFileWalker(context, JavaAnnotations(file), out).walk(file)
    }
}

private class JavaFileWalker(
    private val context: FileContext,
    private val annotations: JavaAnnotations,
    private val out: MutableList<Symbol>,
) {
    fun walk(file: PsiJavaFile) {
        file.classes.forEach { visitClass(it, null) }
    }

    private fun visitClass(declaration: PsiClass, container: JavaContainer?) {
        val draft = draftOf(declaration, container, classKind(declaration)) ?: return
        emit(draft)
        val self = JavaContainer(context.idOf(draft.descriptor))
        declaration.recordComponents.forEach { emit(draftOf(it, self, variableKind("java.field"))) }
        declaration.children.forEach { visitMember(it, self) }
    }

    private fun visitMember(element: PsiElement, container: JavaContainer) {
        when (element) {
            is PsiClass -> visitClass(element, container)
            is PsiMethod -> emit(draftOf(element, container, methodKind(element)))
            is PsiEnumConstant -> emit(draftOf(element, container, variableKind("java.enum-constant")))
            is PsiField -> emit(draftOf(element, container, variableKind("java.field")))
            else -> Unit
        }
    }

    private fun classKind(declaration: PsiClass): SymbolKind = when {
        declaration.isAnnotationType -> typeKind("java.annotation")
        declaration.isInterface -> typeKind("java.interface")
        declaration.isEnum -> typeKind("java.enum")
        declaration.isRecord -> typeKind("java.record")
        else -> typeKind("java.class")
    }

    private fun methodKind(method: PsiMethod): SymbolKind =
        callableKind(if (method.isConstructor) "java.constructor" else "java.method")

    private fun draftOf(member: PsiMember, container: JavaContainer?, kind: SymbolKind): SymbolDraft? {
        val descriptor = JavaDescriptors.of(member)
        val qualifiedName = JavaDescriptors.qualifiedName(member)
        if (descriptor == null || qualifiedName == null) return null
        val uses = annotations.usesOf(member)
        return SymbolDraft(
            kind = kind,
            name = if (member is PsiMethod && member.isConstructor) CONSTRUCTOR_NAME else checkNotNull(member.name),
            qualifiedName = qualifiedName,
            descriptor = descriptor,
            container = container?.id,
            signature = javaSignature(member),
            visibility = javaVisibility(member),
            flags = javaFlags(member, uses) + annotationFlags(uses, annotations.imports),
            declaration = declarationOffsets(member),
            nameRange = (member as? PsiNameIdentifierOwner)?.nameIdentifier?.offsets(),
            documentation = (member as? PsiDocCommentOwner)?.docComment?.let { firstSentence(it.text) },
            attributes = annotationAttributes(uses),
        )
    }

    private fun emit(draft: SymbolDraft?) {
        if (draft != null) out += context.symbolOf(draft)
    }
}
