package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.model.SymbolFlag

private const val MAX_ARGUMENT_LENGTH = 500
private const val TEST_ANNOTATION = "Test"
private val testAnnotations = setOf("org.junit.Test", "org.junit.jupiter.api.Test", "kotlin.test.Test")

/** The explicit and on-demand imports of a file, which decide what a short annotation name stands for. */
internal class ImportScope(val explicit: Map<String, String>, val onDemandPackages: List<String>)

/**
 * An annotation as used on a declaration. [qualifiedName] is null when the annotation could not be resolved to a
 * qualified name, and [writtenName] is then all that is known.
 */
internal class AnnotationUse(val writtenName: String, val qualifiedName: String?, val arguments: Map<String, String>) {
    val displayName: String = qualifiedName ?: writtenName
    val simpleName: String = displayName.substringAfterLast('.')
}

/** Annotations are recorded as attributes of the annotated symbol: their names, and literal arguments by parameter. */
internal fun annotationAttributes(uses: List<AnnotationUse>): Map<String, String> {
    if (uses.isEmpty()) return emptyMap()
    val names = uses.map { it.displayName }.distinct().sorted().joinToString(" ")
    return (listOf("annotations" to names) + uses.flatMap(::argumentAttributes)).toMap()
}

private fun argumentAttributes(use: AnnotationUse): List<Pair<String, String>> =
    use.arguments.map { (name, value) -> "annotation.${use.simpleName}.$name" to value.take(MAX_ARGUMENT_LENGTH) }

internal fun annotationFlags(uses: List<AnnotationUse>, imports: ImportScope): Set<SymbolFlag> = buildSet {
    if (uses.any { it.simpleName == "Deprecated" }) add(SymbolFlag.DEPRECATED)
    if (uses.any { it.simpleName == "JvmStatic" }) add(SymbolFlag.STATIC)
    if (uses.any { isTestAnnotation(it, imports) }) add(SymbolFlag.TEST)
}

private fun isTestAnnotation(use: AnnotationUse, imports: ImportScope): Boolean {
    val qualified = use.qualifiedName ?: return use.simpleName == TEST_ANNOTATION &&
        imports.onDemandPackages.any { "$it.$TEST_ANNOTATION" in testAnnotations }
    return qualified in testAnnotations
}
