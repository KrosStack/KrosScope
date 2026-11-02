package io.github.krosstack.kscope.benchmark.gold

import kotlinx.serialization.Serializable

/**
 * Where gold finds a declaration, independent of KrosScope's identifiers. A code address names [module], [sourceSet],
 * [fqName] and optionally [signature] with the [file] and [line] of the declaration; a non-code address names a [kind]
 * and a [name] in a [file]. [kind] is descriptive, so a consumer uses it as a hint and not as a key. A consumer
 * address names a [file] and the [lines] of it that refer to a declaration; its [usage] says `inferred-type` when it
 * uses a generated type without naming it, and its [useLine] is the line of the call of a query. An [output] address
 * names a file that only a build writes, which the fixture does not contain.
 */
@Serializable
public data class GoldAddress(
    public val module: String? = null,
    public val sourceSet: String? = null,
    public val fqName: String? = null,
    public val signature: String? = null,
    public val kind: String? = null,
    public val name: String? = null,
    public val file: String? = null,
    public val line: Int? = null,
    public val lines: List<Int> = emptyList(),
    public val usage: String? = null,
    public val useLine: Int? = null,
    public val fileFacade: Boolean = false,
    public val compilerGenerated: Boolean = false,
    public val generated: Boolean = false,
    public val output: Boolean = false,
) {
    /** True for a surface that a generator makes, such as an AIDL stub, a Wire message or a SQLDelight query. */
    public val isGeneratedSurface: Boolean
        get() = generated || kind?.startsWith(GENERATED_KIND_PREFIX) == true

    /** A short readable form for reports. */
    public fun describe(): String {
        val subject = fqName ?: name ?: kind ?: "?"
        val where = file?.let { "${it.substringAfterLast('/')}:${line ?: 0}" }.orEmpty()
        return listOf(subject + signature.orEmpty(), where).filter { it.isNotEmpty() }.joinToString(" @ ")
    }

    private companion object {
        const val GENERATED_KIND_PREFIX = "generated."
    }
}
