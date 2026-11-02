package io.github.krosstack.kscope.model

/** A declared parameter type as written in source: its simple name, without type arguments or nullability marks. */
public data class DeclaredParameterType(public val simpleName: String, public val isVararg: Boolean = false)

/**
 * Builds the disambiguator of a [DescriptorSegment.Method] from syntax alone, so that producers that only parse and
 * producers that resolve agree on identity. An extension receiver comes first as `this:<Type>`, a vararg parameter is
 * written `vararg:<Type>` and function types are written [FUNCTION_TYPE].
 *
 * An extension property is identified like a method that takes only its receiver, `name(this:Receiver).`, because
 * extension properties with the same name may exist on different receivers.
 */
public object MethodDisambiguator {
    public const val FUNCTION_TYPE: String = "Function"

    private const val RECEIVER_PREFIX = "this:"
    private const val VARARG_PREFIX = "vararg:"

    public fun render(receiver: String?, parameters: List<DeclaredParameterType>): String {
        val receiverPart = listOfNotNull(receiver?.let { RECEIVER_PREFIX + it })
        val parameterParts = parameters.map { if (it.isVararg) VARARG_PREFIX + it.simpleName else it.simpleName }
        return (receiverPart + parameterParts).joinToString(",")
    }
}
