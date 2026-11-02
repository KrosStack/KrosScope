package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolKind

/** What a member of the generated Java surface is, which the linker reads back from the `aidl.role` attribute. */
internal enum class StubRole(val value: String) {
    INTERFACE("interface"),
    METHOD("method"),
    CONSTANT("constant"),
    DESCRIPTOR("descriptor"),
    DEFAULT("default"),
    STUB("stub"),
    AS_INTERFACE("as-interface"),
    AS_BINDER("as-binder"),
    PARCELABLE("parcelable"),
    FIELD("field"),
    CREATOR("creator"),
}

internal const val ROLE_ATTRIBUTE = "aidl.role"

/**
 * A declaration of the generated surface. The text of the stub and its symbols are both made from these, so that they
 * agree. [truth] is the descriptor of the AIDL symbol the member is generated from, when it has one of its own.
 */
internal sealed interface StubMember {
    val name: String
    val role: StubRole
    val signature: String
    val flags: Set<SymbolFlag>
    val truth: SymbolDescriptor?
}

internal data class StubField(
    override val name: String,
    override val role: StubRole,
    override val signature: String,
    val initializer: String?,
    override val flags: Set<SymbolFlag>,
    override val truth: SymbolDescriptor? = null,
) : StubMember

/** [disambiguator] lists the simple names of the parameter types; a null [body] makes it an interface method. */
internal data class StubMethod(
    override val name: String,
    override val role: StubRole,
    override val signature: String,
    val disambiguator: String,
    val body: String?,
    override val flags: Set<SymbolFlag>,
    override val truth: SymbolDescriptor? = null,
) : StubMember

internal data class StubType(
    override val name: String,
    override val role: StubRole,
    override val signature: String,
    val kind: SymbolKind,
    val members: List<StubMember>,
    override val flags: Set<SymbolFlag>,
    override val truth: SymbolDescriptor? = null,
) : StubMember
