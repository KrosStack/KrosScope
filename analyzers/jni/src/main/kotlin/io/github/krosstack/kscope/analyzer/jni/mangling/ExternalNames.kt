package io.github.krosstack.kscope.analyzer.jni.mangling

import io.github.krosstack.kscope.model.DescriptorSegment
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId

private const val FACADE_SUFFIX = "Kt"

/** A JVM class that may hold a native method, and whether the declaration says so for certain. */
internal class ClassCandidate(val binaryName: String, val isCertain: Boolean)

private fun segmentsOf(id: SymbolId): List<DescriptorSegment> =
    (id.identity() as? ParsedSymbolId.Valid)?.identity?.descriptor?.segments.orEmpty()

private fun binaryName(packageName: List<String>, chain: List<String>): String =
    (packageName + chain.joinToString("$")).joinToString("/")

/**
 * Where the JVM puts a native method that a Kotlin `external` or a Java `native` declaration stands for: the candidate
 * classes, the method name and the parameter types as written. The class of a Kotlin top-level function is the facade
 * of its file; the members of a `companion object` live in the companion class, or in the outer class when they are
 * `@JvmStatic`, which Kotlin may also place in the companion, so both are candidates there.
 */
internal class ExternalNames(
    val classes: List<ClassCandidate>,
    val method: String,
    val parameterTypes: List<String>?,
) {
    companion object {
        /** Null when the id of [symbol] is not a method of a workspace class. */
        fun of(symbol: Symbol, isInCompanion: Boolean): ExternalNames? {
            val segments = segmentsOf(symbol.id)
            val method = segments.lastOrNull() as? DescriptorSegment.Method
            val packageName = segments.filterIsInstance<DescriptorSegment.Namespace>().map { it.name }
            val types = segments.filterIsInstance<DescriptorSegment.Type>().map { it.name }
            val owners = classCandidates(symbol, packageName, types, isInCompanion)
            return if (method == null || owners == null) {
                null
            } else {
                ExternalNames(owners, method.name, ExternalParameters.of(symbol, method.name))
            }
        }

        private fun classCandidates(
            symbol: Symbol,
            packageName: List<String>,
            types: List<String>,
            isInCompanion: Boolean,
        ): List<ClassCandidate>? {
            val chain = types.ifEmpty { listOfNotNull(facadeOf(symbol)) }
            if (chain.isEmpty()) return null
            val own = ClassCandidate(binaryName(packageName, chain), true)
            val outer = ClassCandidate(binaryName(packageName, chain.dropLast(1)), true)
            return when {
                !isInCompanion || chain.size < 2 -> listOf(own)
                SymbolFlag.STATIC in symbol.flags -> listOf(outer, ClassCandidate(own.binaryName, false))
                else -> listOf(own)
            }
        }

        private fun facadeOf(symbol: Symbol): String? {
            val path = symbol.declaration?.artifact?.path?.value ?: return null
            val base = path.substringAfterLast('/').substringBeforeLast('.')
            return base.map { if (it.isLetterOrDigit() || it == '_') it else '_' }.joinToString("")
                .replaceFirstChar { it.uppercase() } + FACADE_SUFFIX
        }
    }
}

/** The binary name `pkg/Outer$Inner` of a class symbol, from its id. */
internal fun binaryNameOf(id: SymbolId): String? {
    val segments = segmentsOf(id)
    val packageName = segments.filterIsInstance<DescriptorSegment.Namespace>().map { it.name }
    val types = segments.filterIsInstance<DescriptorSegment.Type>().map { it.name }
    return if (types.isEmpty()) null else binaryName(packageName, types)
}
