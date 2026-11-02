package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.mangling.ExternalNames
import io.github.krosstack.kscope.analyzer.jni.mangling.JniMangler
import io.github.krosstack.kscope.analyzer.jni.mangling.JvmDescriptors
import io.github.krosstack.kscope.model.Symbol

internal const val JNI_FUNCTION_KIND = "jni.function"
internal const val JNI_GENERATOR = "jni"

/** A mangled name, and whether it is the long form and whether the declaration makes the class certain. */
internal class JniName(val value: String, val isLong: Boolean, val isCertain: Boolean)

/**
 * The contract of one native method: the names that a native function may have to implement it. The short form of
 * every candidate class is a name; the long form is one too when all parameter types are known.
 */
internal class JniContract(val external: Symbol, val names: List<JniName>) {
    val shortName: String get() = names.first { !it.isLong }.value
    val longName: String? get() = names.firstOrNull { it.isLong }?.value
    val hasAlternateClass: Boolean get() = names.any { !it.isCertain }

    fun nameOf(nativeName: String): JniName? = names.firstOrNull { it.value == nativeName }

    companion object {
        fun of(external: Symbol, isInCompanion: Boolean, descriptors: JvmDescriptors): JniContract? {
            val owner = ExternalNames.of(external, isInCompanion) ?: return null
            val parameters = owner.parameterTypes?.map { descriptors.of(it) }
            val knownParameters = parameters?.takeIf { list -> list.all { it != null } }?.filterNotNull()
            val names = owner.classes.flatMap { candidate ->
                listOfNotNull(
                    JniName(JniMangler.shortName(candidate.binaryName, owner.method), false, candidate.isCertain),
                    knownParameters?.let {
                        JniName(JniMangler.longName(candidate.binaryName, owner.method, it), true, candidate.isCertain)
                    },
                )
            }
            return JniContract(external, names)
        }
    }
}
