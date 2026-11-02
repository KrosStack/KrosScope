package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.AnalysisPlan
import io.github.krosstack.kscope.analyzer.kotlin.planning.ModelDsl
import io.github.krosstack.kscope.analyzer.kotlin.planning.model
import io.github.krosstack.kscope.analyzer.kotlin.planning.planAll
import io.github.krosstack.kscope.analyzer.kotlin.planning.sourceSetId
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class HeuristicReferencesTest {
    private val modules = listOf(":app", ":lib", ":base", ":other")

    private fun plan(dependencies: ModelDsl.() -> Unit): AnalysisPlan = model {
        modules.forEach { path ->
            module(path) {
                target("jvm", Platform.JVM)
                sourceSet("main", targets = listOf("jvm"))
            }
        }
        dependencies()
    }.planAll()

    private fun symbolIn(module: String) = SymbolIdentity.workspace(
        ModuleId("repo//$module"),
        "main",
        SymbolDescriptor().namespace("org").namespace("example").type("Greeter"),
    ).toSymbolId().let(NodeRef::OfSymbol)

    private val libraryClass = SymbolIdentity
        .library(SymbolEcosystem.MAVEN, "org.example:other:1.0", SymbolDescriptor().type("Thing"))
        .toSymbolId().let(NodeRef::OfSymbol)

    private fun reference(target: NodeRef, certainty: Certainty) = Relationship(
        from = symbolIn(":app"),
        kind = RelationKind.CALLS,
        subKind = "member-call",
        resolution = Resolution.Resolved(target, certainty),
        evidenceClass = EvidenceClass.COMPILER,
        site = null,
        scope = Scope(sourceSet = sourceSetId(":app", "main")),
    )

    private fun lowered(plan: AnalysisPlan, vararg references: Relationship): List<Relationship> {
        val batch = FactBatch(
            ProducerRef("kscope.analyzer.kotlin", "0.1.0"),
            OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSetId(":app", "main").value),
            Fingerprint("f"),
            references.map { Fact.OfRelationship(it) },
        )
        return HeuristicReferences.applyTo(batch, plan).facts.map { (it as Fact.OfRelationship).value }
    }

    private fun Relationship.certainty() = (resolution as Resolution.Resolved).certainty

    @Test
    fun `a reference resolved into a module reached over a heuristic dependency becomes heuristic`() {
        val plan = plan { dependency(":app", ":lib", certainty = Certainty.HEURISTIC) }
        val exact = reference(symbolIn(":lib"), Certainty.EXACT)
        val probable = reference(symbolIn(":lib"), Certainty.PROBABLE)

        val result = lowered(plan, exact, probable)

        assertEquals(listOf(Certainty.HEURISTIC, Certainty.HEURISTIC), result.map { it.certainty() })
        assertEquals(listOf(exact.subKind, probable.subKind), result.map { it.subKind })
        assertEquals(listOf(EvidenceClass.COMPILER, EvidenceClass.COMPILER), result.map { it.evidenceClass })
        assertEquals(exact.copy(resolution = Resolution.Resolved(symbolIn(":lib"), Certainty.HEURISTIC)), result[0])
    }

    @Test
    fun `a reference resolved into a module reached over exact dependencies keeps its certainty`() {
        val plan = plan { dependency(":app", ":lib") }
        val exact = reference(symbolIn(":lib"), Certainty.EXACT)
        val probable = reference(symbolIn(":lib"), Certainty.PROBABLE)

        assertEquals(listOf(exact, probable), lowered(plan, exact, probable))
    }

    @Test
    fun `a module behind a heuristic dependency further down is heuristic and the one before it is not`() {
        val plan = plan {
            dependency(":app", ":lib")
            dependency(":lib", ":base", configuration = "api", certainty = Certainty.HEURISTIC)
        }
        val toLib = reference(symbolIn(":lib"), Certainty.EXACT)
        val toBase = reference(symbolIn(":base"), Certainty.EXACT)

        assertEquals(listOf(Certainty.EXACT, Certainty.HEURISTIC), lowered(plan, toLib, toBase).map { it.certainty() })
    }

    @Test
    fun `an exact path to a module keeps its certainty only when the compiler sees that path`() {
        val certainties = listOf("api", "implementation").map { configuration ->
            val plan = plan {
                dependency(":app", ":lib")
                dependency(":app", ":base", certainty = Certainty.HEURISTIC)
                dependency(":lib", ":base", configuration = configuration)
            }
            lowered(plan, reference(symbolIn(":base"), Certainty.EXACT)).single().certainty()
        }

        assertEquals(listOf(Certainty.EXACT, Certainty.HEURISTIC), certainties)
    }

    @Test
    fun `the own module, library symbols, heuristic and unresolved references stay as they are`() {
        val plan = plan { dependency(":app", ":lib", certainty = Certainty.HEURISTIC) }
        val own = reference(symbolIn(":app"), Certainty.EXACT)
        val library = reference(libraryClass, Certainty.PROBABLE)
        val heuristic = reference(symbolIn(":lib"), Certainty.HEURISTIC)
        val unresolved = own.copy(resolution = Resolution.Unresolved("Greeter", ReasonCode("kotlin.unresolved")))

        assertEquals(listOf(own, library, heuristic, unresolved), lowered(plan, own, library, heuristic, unresolved))
    }

    @Test
    fun `a module that is not reached at all is left alone`() {
        val plan = plan { dependency(":app", ":lib", certainty = Certainty.HEURISTIC) }
        val toOther = reference(symbolIn(":other"), Certainty.PROBABLE)

        assertEquals(listOf(toOther), lowered(plan, toOther))
    }
}
