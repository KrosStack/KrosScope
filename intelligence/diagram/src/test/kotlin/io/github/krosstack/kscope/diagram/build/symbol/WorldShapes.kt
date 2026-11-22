package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind

private val functionKind = SymbolKind("kotlin.function", SymbolCategory.CALLABLE)

internal fun SymbolWorld.opaqueFunction(name: String, owner: SymbolId): SymbolId {
    val declaration = Declaration(name, functionKind, owner, modules.getValue(owner), files.getValue(owner))
    return enroll(SymbolId("legacy $name"), declaration, "${symbols.getValue(owner).qualifiedName}.$name")
}

internal fun SymbolWorld.facadeFunction(name: String, facade: String): SymbolId {
    val descriptor = SymbolDescriptor().type(facade).method(name)
    val id = SymbolIdentity.workspace(ModuleId("$REPOSITORY//:app"), "main", descriptor).toSymbolId()
    return enroll(id, Declaration(name, functionKind, null, "app", "src/main/$facade.kt"), "app.$name")
}

internal fun SymbolWorld.callOutside(from: SymbolId) {
    val target = Resolution.Resolved(NodeRef.OfExternal("maven:org.example:lib"), Certainty.EXACT)
    relationships += Relationship(
        NodeRef.OfSymbol(from),
        RelationKind.CALLS,
        null,
        target,
        EvidenceClass.COMPILER,
        null,
        Scope(),
    )
}

internal fun SymbolWorld.callMissing(from: SymbolId, line: Int): SymbolId {
    val ghost = SymbolId("ghost $line")
    call(from, ghost, line)
    return ghost
}
