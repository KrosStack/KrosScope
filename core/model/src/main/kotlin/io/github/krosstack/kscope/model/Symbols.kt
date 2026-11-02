package io.github.krosstack.kscope.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class SymbolCategory {
    @SerialName("package")
    PACKAGE,

    @SerialName("type")
    TYPE,

    @SerialName("callable")
    CALLABLE,

    @SerialName("variable")
    VARIABLE,

    @SerialName("resource")
    RESOURCE,

    @SerialName("component")
    COMPONENT,

    @SerialName("contract")
    CONTRACT,

    @SerialName("schema")
    SCHEMA,

    @SerialName("build")
    BUILD,
}

/** The id is open and namespaced: `kotlin.class`, `java.method`, `android.resource.string`, `aidl.method`. */
@Serializable
public data class SymbolKind(public val id: String, public val category: SymbolCategory)

@Serializable
public enum class Visibility {
    @SerialName("public")
    PUBLIC,

    @SerialName("protected")
    PROTECTED,

    @SerialName("internal")
    INTERNAL,

    @SerialName("private")
    PRIVATE,

    @SerialName("local")
    LOCAL,
}

@Serializable
public enum class SymbolFlag {
    @SerialName("expect")
    EXPECT,

    @SerialName("actual")
    ACTUAL,

    @SerialName("external")
    EXTERNAL,

    @SerialName("abstract")
    ABSTRACT,

    @SerialName("open")
    OPEN,

    @SerialName("override")
    OVERRIDE,

    @SerialName("static")
    STATIC,

    @SerialName("suspend")
    SUSPEND,

    @SerialName("test")
    TEST,

    @SerialName("deprecated")
    DEPRECATED,
}

@Serializable
public sealed interface SymbolOrigin {
    @Serializable
    @SerialName("source")
    public data object Source : SymbolOrigin

    @Serializable
    @SerialName("generated")
    public data class Generated(public val generator: String, public val sourceOfTruth: NodeRef?) : SymbolOrigin

    /** Members added by a compiler plugin. */
    @Serializable
    @SerialName("synthetic")
    public data class Synthetic(public val plugin: String) : SymbolOrigin

    @Serializable
    @SerialName("library")
    public data class Library(public val coordinate: String?) : SymbolOrigin
}

/**
 * [signature] is a one-line human-readable declaration header. [declaration] spans the whole declaration including
 * its body, [nameLocation] only the identifier. [documentation] is the first sentence of the KDoc or Javadoc, kept
 * for lexical retrieval. [attributes] are producer-specific and serialized sorted by key.
 */
@Serializable
public data class Symbol(
    public val id: SymbolId,
    public val kind: SymbolKind,
    public val name: String,
    public val qualifiedName: String?,
    public val signature: String?,
    public val container: SymbolId?,
    public val visibility: Visibility?,
    @Serializable(with = SymbolFlagSetSerializer::class)
    public val flags: Set<SymbolFlag>,
    public val origin: SymbolOrigin,
    public val declaration: SourceLocation?,
    public val nameLocation: SourceLocation?,
    public val documentation: String?,
    public val scope: Scope,
    public val evidenceClass: EvidenceClass,
    public val certainty: Certainty,
    @Serializable(with = SortedAttributesSerializer::class)
    public val attributes: Map<String, String> = emptyMap(),
)
