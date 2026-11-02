package io.github.krosstack.kscope.benchmark.gold

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One outgoing edge of a closed-world file: [kind] says how the source at [line] and [column] names the [target]. */
@Serializable
public data class GoldEdge(
    public val line: Int,
    public val column: Int,
    public val text: String,
    public val kind: String,
    public val target: GoldAddress,
)

/** An import of a workspace symbol, listed apart from the edges. */
@Serializable
public data class GoldImport(
    public val line: Int,
    public val target: GoldAddress,
    @SerialName("import") public val importedName: String,
)

/**
 * A declaration of the file itself, derived from reading the source. [kind] is one of `class`, `interface`, `object`,
 * `enum-entry`, `typealias`, `function`, `property` and `constructor`, the same for Kotlin and Java; [line] is the line
 * of the declaration's name, for a primary constructor the line of its opening parenthesis. [fqName] of a constructor
 * is the qualified name of its class followed by `.<init>`.
 */
@Serializable
public data class GoldDeclaration(public val kind: String, public val fqName: String, public val line: Int)

/**
 * The exhaustive outgoing reference, call and inheritance edges of one source file to workspace or generated symbols,
 * and the declarations the file makes.
 */
@Serializable
public data class GoldClosedWorldFile(
    public val workspaceRoot: String,
    public val file: String,
    public val module: String,
    public val sourceSet: String,
    public val language: String,
    public val completeness: String,
    public val notes: String? = null,
    public val imports: List<GoldImport> = emptyList(),
    public val edges: List<GoldEdge> = emptyList(),
    public val declarations: List<GoldDeclaration> = emptyList(),
)
