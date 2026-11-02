package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.stub.StubRole
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol

internal const val INFERRED_ATTRIBUTE = "aidl.inferred"
private const val ANONYMOUS = "anonymous"
private const val ANONYMOUS_OBJECT = "anonymous-object"

/**
 * The overrides in an object expression, which the compiler relationships do not state because the members of an
 * object expression are not symbols. An object expression that extends the `Stub` or implements the interface has to
 * override every method of the interface, or the code does not compile; so each method is bound to the symbol around
 * the object, as probable and marked inferred. An override that a relationship does state wins, and an object that
 * extends `Default` overrides only some methods, so it is left out.
 */
internal class AnonymousOverrides(private val methods: List<Symbol>) {
    fun bindings(supertypes: List<SupertypeEdge>, known: List<Binding>): List<Binding> {
        val stated = known.map { it.from.id to it.target }.toSet()
        return supertypes.filter(::isObjectOverridingEverything).flatMap { supertype ->
            methods.filter { (it.id to supertype.edge.from) !in stated }.map { bindingOf(it, supertype) }
        }.distinctBy { it.from.id to it.target }
    }

    private fun isObjectOverridingEverything(supertype: SupertypeEdge): Boolean =
        supertype.edge.subKind == ANONYMOUS && supertype.edge.from is NodeRef.OfSymbol &&
            supertype.role != StubRole.DEFAULT.value

    private fun bindingOf(method: Symbol, supertype: SupertypeEdge) = Binding(
        from = method,
        target = supertype.edge.from,
        subKind = OVERRIDE,
        certainty = maxOf(Certainty.PROBABLE, supertype.edge.certainty()),
        scope = supertype.edge.scope,
        attributes = mapOf(INFERRED_ATTRIBUTE to ANONYMOUS_OBJECT),
    )
}
