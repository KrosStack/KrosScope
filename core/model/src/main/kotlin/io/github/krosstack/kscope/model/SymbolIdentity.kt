package io.github.krosstack.kscope.model

public enum class SymbolEcosystem(public val token: String) {
    GRADLE("gradle"),
    MAVEN("maven"),
    JDK("jdk"),
    UNKNOWN("unknown"),
}

/**
 * The parts of a [SymbolId]: `kscope <ecosystem> <unit> <source set> <descriptor>`. A part holding a space is written
 * with the space doubled; parts must not start or end with a space.
 */
public data class SymbolIdentity(
    public val ecosystem: SymbolEcosystem,
    public val unit: String,
    public val sourceSet: String,
    public val descriptor: SymbolDescriptor,
) {
    init {
        requireWellFormedParts(this)
    }

    public fun toSymbolId(): SymbolId {
        val parts = listOf(SYMBOL_ID_SCHEME, ecosystem.token, unit, sourceSet, descriptor.render())
        return SymbolId(parts.joinToString(" ") { it.replace(" ", "  ") })
    }

    public companion object {
        public fun workspace(module: ModuleId, sourceSet: String?, descriptor: SymbolDescriptor): SymbolIdentity =
            SymbolIdentity(SymbolEcosystem.GRADLE, module.value, sourceSet ?: NOT_APPLICABLE, descriptor)

        public fun library(
            ecosystem: SymbolEcosystem,
            coordinate: String?,
            descriptor: SymbolDescriptor,
        ): SymbolIdentity {
            require(ecosystem != SymbolEcosystem.GRADLE) { "Library symbols never use the gradle ecosystem" }
            return SymbolIdentity(ecosystem, coordinate ?: NOT_APPLICABLE, NOT_APPLICABLE, descriptor)
        }
    }
}

internal const val SYMBOL_ID_SCHEME: String = "kscope"

private const val NOT_APPLICABLE = "."

internal fun partProblem(label: String, part: String): String? = when {
    part.isEmpty() -> "The $label must not be empty"
    part.startsWith(' ') || part.endsWith(' ') -> "The $label must not start or end with a space"
    else -> null
}

private fun requireWellFormedParts(identity: SymbolIdentity) {
    val problem = partProblem("unit", identity.unit)
        ?: partProblem("source set", identity.sourceSet)
        ?: partProblem("descriptor", identity.descriptor.render())
    require(problem == null) { problem.orEmpty() }
}
