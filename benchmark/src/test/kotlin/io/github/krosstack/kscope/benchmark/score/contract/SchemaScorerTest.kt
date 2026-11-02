package io.github.krosstack.kscope.benchmark.score.contract

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SymbolDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaScorerTest {
    private val proto = "sync/src/main/proto/sync.proto"
    private val named = "app/src/main/kotlin/p/Named.kt"
    private val inferred = "app/src/main/kotlin/p/Inferred.kt"
    private val both = "app/src/main/kotlin/p/Both.kt"

    private val protoType = SymbolDescriptor().namespace("proto").type("TaskDto")
    private val message = Graphs.symbol(proto, "p.TaskDto", "proto.message", 13, protoType).ofKind("proto.message")
    private val idField = Graphs.symbol(proto, "p.TaskDto.id", "proto.field", 14, protoType.term("id"))
        .ofKind("proto.field")
    private val dtoClass = Graphs.symbol("", "p.TaskDto", "kotlin.class", 1, Graphs.type("TaskDto")).madeBy("wire")
    private val idProperty = Graphs.symbol("", "p.TaskDto.id", "kotlin.property", 1, Graphs.type("TaskDto").term("id"))
        .madeBy("wire", dtoClass)
    private val users = listOf("Named", "Inferred", "Both").map { name ->
        Graphs.symbol("app/src/main/kotlin/p/$name.kt", "p.$name", "kotlin.class", 3, Graphs.type(name))
    }

    private val graph = coreGraph(
        symbols = listOf(message, idField, dtoClass, idProperty) + users,
        files = listOf(named, inferred, both),
        relationships = listOf(
            schemaEdge(message, dtoClass, RelationKind.GENERATES, EvidenceClass.CONVENTION),
            schemaEdge(idField, idProperty, RelationKind.GENERATES, EvidenceClass.CONVENTION),
            schemaEdge(users[0], dtoClass, RelationKind.TYPE_USES, EvidenceClass.COMPILER, named to 5),
            schemaEdge(users[1], idProperty, RelationKind.REFERENCES, EvidenceClass.COMPILER, inferred to 9),
            schemaEdge(users[2], dtoClass, RelationKind.TYPE_USES, EvidenceClass.COMPILER, both to 4),
            schemaEdge(users[2], idProperty, RelationKind.REFERENCES, EvidenceClass.COMPILER, both to 8),
        ),
    )

    private val messageAddress =
        GoldAddress(module = ":core", file = proto, kind = "proto.message", name = "TaskDto", line = 13)

    private fun consumer(file: String, usage: String? = null) =
        GoldAddress(file = file, kind = "class", lines = listOf(1), usage = usage)

    private fun score(vararg consumers: Pair<String, String?>): ClassScore {
        val (index, resolver) = scoringOf(graph)
        val relationships = consumers.mapIndexed { i, (file, usage) ->
            schemaGold("p:$i", "proto_message_to_consumer", messageAddress, consumer(file, usage), "generated-artifact")
        }
        return protoScorer(index, resolver).score(relationships)
    }

    @Test
    fun `a file that names the generated type is a named consumer and convention satisfies generated artifact`() {
        val result = score(named to null)
        val part = result.parts.single { it.label == "named" }
        assertEquals(1, part.score.matched)
        assertEquals(0, result.evidenceMismatches)
        assertEquals(mapOf("convention" to 1), result.acceptedEvidence)
    }

    @Test
    fun `a file that only reads members of the type uses it by inference`() {
        val result = score(inferred to "inferred-type")
        assertEquals(1, result.parts.single { it.label == "inferred-type" }.score.matched)
        assertEquals(1.0, result.score?.recall)
    }

    @Test
    fun `a file that names the type and also reads its members is one named consumer and nothing more`() {
        val result = score(both to null)
        assertEquals(1, result.parts.single { it.label == "named" }.score.matched)
        assertEquals(emptyList(), result.score?.extras.orEmpty().filter { "Both.kt" in it })
    }

    @Test
    fun `a consumer that the graph does not have is a miss and one that gold does not list is an extra`() {
        val result = score(named to null, "app/src/main/kotlin/p/Missing.kt" to null)
        assertEquals(listOf("p:1 app/src/main/kotlin/p/Missing.kt"), result.score?.misses)
        assertEquals(2, result.score?.extras?.size, "Inferred.kt and Both.kt are consumers gold did not list")
    }

    @Test
    fun `an unmapped source is reported and counted as a miss`() {
        val (index, resolver) = scoringOf(graph)
        val unknown = messageAddress.copy(name = "Unknown")
        val gold = schemaGold("p:1", "proto_message_to_consumer", unknown, consumer(named), "generated-artifact")
        val result = protoScorer(index, resolver).score(listOf(gold))
        assertEquals(1, result.unmapped.size)
        assertEquals(1, result.score?.misses?.size)
    }
}
