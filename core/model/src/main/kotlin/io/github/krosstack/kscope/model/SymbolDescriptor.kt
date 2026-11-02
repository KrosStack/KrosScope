package io.github.krosstack.kscope.model

public sealed interface DescriptorSegment {
    public val name: String

    public data class Namespace(override val name: String) : DescriptorSegment

    public data class Type(override val name: String) : DescriptorSegment

    public data class Term(override val name: String) : DescriptorSegment

    /** The disambiguator lists the declared parameter types and must not contain parentheses. */
    public data class Method(override val name: String, public val disambiguator: String = "") : DescriptorSegment {
        init {
            require('(' !in disambiguator && ')' !in disambiguator) {
                "A method disambiguator must not contain parentheses: '$disambiguator'"
            }
        }
    }

    public data class TypeParameter(override val name: String) : DescriptorSegment

    public data class Parameter(override val name: String) : DescriptorSegment

    public data class Meta(override val name: String) : DescriptorSegment
}

public data class SymbolDescriptor(public val segments: List<DescriptorSegment> = emptyList()) {
    public fun namespace(name: String): SymbolDescriptor = plus(DescriptorSegment.Namespace(name))

    public fun type(name: String): SymbolDescriptor = plus(DescriptorSegment.Type(name))

    public fun term(name: String): SymbolDescriptor = plus(DescriptorSegment.Term(name))

    public fun method(name: String, disambiguator: String = ""): SymbolDescriptor =
        plus(DescriptorSegment.Method(name, disambiguator))

    public fun extensionProperty(name: String, receiver: String): SymbolDescriptor =
        method(name, MethodDisambiguator.render(receiver, emptyList()))

    public fun typeParameter(name: String): SymbolDescriptor = plus(DescriptorSegment.TypeParameter(name))

    public fun parameter(name: String): SymbolDescriptor = plus(DescriptorSegment.Parameter(name))

    public fun meta(name: String): SymbolDescriptor = plus(DescriptorSegment.Meta(name))

    public operator fun plus(segment: DescriptorSegment): SymbolDescriptor = SymbolDescriptor(segments + segment)

    public fun render(): String = segments.joinToString("") { it.render() }
}

private const val QUOTE = '`'

internal fun Char.isPlainNameCharacter(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in "_+-$"

private fun quoted(name: String): String {
    val isPlain = name.isNotEmpty() && name.all { it.isPlainNameCharacter() }
    return if (isPlain) name else "$QUOTE${name.replace("$QUOTE", "$QUOTE$QUOTE")}$QUOTE"
}

private fun DescriptorSegment.render(): String = when (this) {
    is DescriptorSegment.Namespace -> "${quoted(name)}/"
    is DescriptorSegment.Type -> "${quoted(name)}#"
    is DescriptorSegment.Term -> "${quoted(name)}."
    is DescriptorSegment.Method -> "${quoted(name)}($disambiguator)."
    is DescriptorSegment.TypeParameter -> "[${quoted(name)}]"
    is DescriptorSegment.Parameter -> "(${quoted(name)})"
    is DescriptorSegment.Meta -> "${quoted(name)}:"
}
