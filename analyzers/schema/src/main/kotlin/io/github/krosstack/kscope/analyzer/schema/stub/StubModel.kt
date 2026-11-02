package io.github.krosstack.kscope.analyzer.schema.stub

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.SymbolId

/**
 * What a generated declaration comes from. A [direct] declaration is the generated counterpart of [source] and gets a
 * `GENERATES` relationship from it; any other declaration only names its source of truth.
 */
internal data class StubTruth(val source: SymbolId, val direct: Boolean = false)

internal data class StubParameter(
    val name: String,
    val type: String,
    val default: String? = null,
    val isProperty: Boolean = false,
    val isOverride: Boolean = false,
    val truth: StubTruth? = null,
    val certainty: Certainty = Certainty.EXACT,
    val typeSource: SymbolId? = null,
)

/** A member declared in the body of a class: a property or a function. */
internal sealed interface StubMember {
    val name: String
    val truth: StubTruth?
    val certainty: Certainty
}

/** A property. Without an [initializer] it is abstract, as in an interface. */
internal data class StubProperty(
    override val name: String,
    val type: String,
    val initializer: String? = STUB_BODY,
    override val truth: StubTruth? = null,
    override val certainty: Certainty = Certainty.EXACT,
    val typeSource: SymbolId? = null,
) : StubMember

/** A function. Without a body it is abstract. [typeParameters] is the text of the angle brackets, if any. */
internal data class StubFunction(
    override val name: String,
    val parameters: List<StubParameter>,
    val returnType: String,
    val typeParameters: String? = null,
    val isOperator: Boolean = false,
    val hasBody: Boolean = true,
    override val truth: StubTruth? = null,
    override val certainty: Certainty = Certainty.EXACT,
    val typeSource: SymbolId? = null,
) : StubMember

internal data class StubEntry(val name: String, val arguments: String, val truth: StubTruth? = null)

internal enum class StubForm(val keyword: String) {
    CLASS("class"),
    DATA_CLASS("data class"),
    ENUM_CLASS("enum class"),
    INTERFACE("interface"),
    COMPANION("companion object"),
}

/** A class with its primary constructor, supertypes, enum entries, members and nested classes. */
internal data class StubClass(
    val name: String,
    val form: StubForm,
    val constructor: List<StubParameter>? = null,
    val superTypes: String? = null,
    val entries: List<StubEntry> = emptyList(),
    val members: List<StubMember> = emptyList(),
    val nested: List<StubClass> = emptyList(),
    val truth: StubTruth? = null,
    val certainty: Certainty = Certainty.EXACT,
)

internal data class StubFile(val packageName: String, val classes: List<StubClass>)

internal const val STUB_BODY = "TODO()"
