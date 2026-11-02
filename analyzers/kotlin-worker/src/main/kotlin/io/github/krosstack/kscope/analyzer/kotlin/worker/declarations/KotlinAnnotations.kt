package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.analyzer.kotlin.worker.syntax.literalValue
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtUserType

private const val DEFAULT_PARAMETER = "value"

/** Reads the annotations of Kotlin declarations: names, qualified as far as the file and the classpath allow. */
internal class KotlinAnnotations(private val qualify: (KtAnnotationEntry) -> String?, file: KtFile) {
    val imports: ImportScope = importScope(file)

    fun usesOf(annotated: KtAnnotated): List<AnnotationUse> = annotated.annotationEntries.mapNotNull(::useOf)

    private fun useOf(entry: KtAnnotationEntry): AnnotationUse? {
        val written = writtenName(entry) ?: return null
        val qualified = when {
            '.' in written -> written
            else -> imports.explicit[written] ?: qualify(entry)
        }
        return AnnotationUse(written, qualified, argumentsOf(entry))
    }

    private fun writtenName(entry: KtAnnotationEntry): String? {
        val type = entry.typeReference?.typeElement as? KtUserType ?: return null
        return generateSequence(type) { it.qualifier }.mapNotNull { it.referencedName }.toList().asReversed()
            .joinToString(".").ifEmpty { null }
    }

    private fun argumentsOf(entry: KtAnnotationEntry): Map<String, String> {
        val arguments = entry.valueArguments
        return arguments.mapNotNull { argument ->
            val name = argument.getArgumentName()?.asName?.asString()
                ?: DEFAULT_PARAMETER.takeIf { arguments.size == 1 }
            val literal = literalValue(argument.getArgumentExpression())
            if (name == null || literal == null) null else name to literal
        }.toMap()
    }
}

private fun importScope(file: KtFile): ImportScope {
    val directives = file.importDirectives.filter { it.importedFqName != null }
    val explicit = directives.filterNot { it.isAllUnder }.associate { directive ->
        val fqName = checkNotNull(directive.importedFqName)
        (directive.aliasName ?: fqName.shortName().asString()) to fqName.asString()
    }
    val onDemand = directives.filter { it.isAllUnder }.map { checkNotNull(it.importedFqName).asString() }
    return ImportScope(explicit, onDemand)
}
