package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IntegrityTest {
    private val module = SampleWorkspace.singleModule("shapes", "shapes/main")
    private val emitted = SampleWorkspace.singleModule("shapes", "shapes/main").sourceSets.single().sourceSet
    private val unit = ModuleId("sample//:shapes")

    private fun workspaceId(name: String, sourceSet: String = "main"): SymbolId =
        SymbolIdentity.workspace(unit, sourceSet, SymbolDescriptor().namespace("p").term(name)).toSymbolId()

    private fun relationship(from: SymbolId, target: SymbolId) = Relationship(
        from = NodeRef.OfSymbol(from),
        kind = RelationKind.REFERENCES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(target), Certainty.EXACT),
        evidenceClass = EvidenceClass.COMPILER,
        site = null,
        scope = Scope(sourceSet = emitted),
    )

    private fun batch(vararg relationships: Relationship) = FactBatch(
        WorkerIdentity.producer,
        OwnershipUnit(OwnershipKind.SOURCE_SET, emitted.value),
        Fingerprint("0"),
        relationships.map { Fact.OfRelationship(it) },
    )

    @Test
    fun `a resolved workspace target that no batch emitted is reported`() {
        val batch = batch(relationship(workspaceId("a"), workspaceId("missing")))
        val failure = assertFailsWith<AssertionError> {
            Integrity.assertNoDanglingTargets(listOf(batch), listOf(module)) { it == workspaceId("a") }
        }
        assertTrue(failure.message.orEmpty().contains("shapes main p/missing."))
    }

    @Test
    fun `a target that a generator emits is not reported`() {
        val batch = batch(relationship(workspaceId("a"), workspaceId("generated")))
        val generated = workspaceId("generated")
        Integrity.assertNoDanglingTargets(listOf(batch), listOf(module)) { it == generated || it == workspaceId("a") }
    }

    @Test
    fun `targets in library ecosystems and in source sets nobody asked for are not reported`() {
        val library = SymbolIdentity.library(SymbolEcosystem.JDK, null, SymbolDescriptor().term("x")).toSymbolId()
        val batch = batch(
            relationship(workspaceId("a"), library),
            relationship(workspaceId("a"), workspaceId("b", "other")),
        )
        Integrity.assertNoDanglingTargets(listOf(batch), listOf(module)) { it == workspaceId("a") }
    }
}
