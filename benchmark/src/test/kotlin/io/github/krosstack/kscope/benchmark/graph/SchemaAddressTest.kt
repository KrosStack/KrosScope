package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.benchmark.score.contract.coreGraph
import io.github.krosstack.kscope.benchmark.score.contract.madeBy
import io.github.krosstack.kscope.benchmark.score.contract.ofKind
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SchemaAddressTest {
    private val sq = "db/src/commonMain/sqldelight/p/Task.sq"
    private val proto = "sync/src/main/proto/sync.proto"
    private val schema = "settings/schemas/p.Db/1.json"

    private val table = Graphs.symbol(
        sq,
        "p.TaskRow",
        "sqldelight.table",
        1,
        SymbolDescriptor().namespace("t").type("T"),
    )
        .ofKind("sqldelight.table")
    private val message = Graphs.symbol(
        proto,
        "p.TaskDto",
        "proto.message",
        13,
        SymbolDescriptor().namespace("m").type("M"),
    )
        .ofKind("proto.message")
    private val enumeration = Graphs.symbol(
        proto,
        "p.Priority",
        "proto.enum",
        5,
        SymbolDescriptor().namespace("m").type("E"),
    )
        .ofKind("proto.enum")
    private val plain = Graphs.symbol(
        "",
        "p.TaskQueries.selectAll",
        "kotlin.function",
        1,
        Graphs.method("Q", "selectAll", ""),
    )
        .copy(signature = "public fun selectAll(): Query<TaskRow>").madeBy("sqldelight")
        .ofKind("kotlin.function", SymbolCategory.CALLABLE)
    private val mapper = Graphs.symbol(
        "",
        "p.TaskQueries.selectAll",
        "kotlin.function",
        1,
        Graphs.method("Q", "selectAll", "Function"),
    ).copy(signature = "public fun <T : Any> selectAll(mapper: (id: String) -> T): Query<T>").madeBy("sqldelight")
        .ofKind("kotlin.function", SymbolCategory.CALLABLE)

    private val graph = coreGraph(
        symbols = listOf(table, message, enumeration, plain, mapper),
        files = listOf(sq, proto, schema),
        relationships = emptyList(),
    )
    private val resolver = GoldAddressResolver(graph)

    private fun nodeOf(address: GoldAddress): NodeRef = assertIs<AddressResolution.Found>(
        resolver.resolve(address),
    ).node

    @Test
    fun `proto and sqldelight sources are found by kind, name and file`() {
        assertEquals(
            NodeRef.OfSymbol(message.id),
            nodeOf(GoldAddress(module = ":core", file = proto, kind = "proto.message", name = "TaskDto", line = 13)),
        )
        assertEquals(
            NodeRef.OfSymbol(enumeration.id),
            nodeOf(GoldAddress(module = ":core", file = proto, kind = "proto.enum", name = "Priority", line = 5)),
        )
        assertEquals(
            NodeRef.OfSymbol(table.id),
            nodeOf(GoldAddress(module = ":core", file = sq, kind = "sq.table", name = "TaskRow", line = 1)),
        )
    }

    @Test
    fun `a Room schema export is the artifact of its file`() {
        val address = GoldAddress(module = ":core", file = schema, kind = "room.schema", name = "Db@1", line = 1)
        assertEquals(NodeRef.OfArtifact(Graphs.ref(schema)), nodeOf(address))
    }

    @Test
    fun `gold names a generated function once and that is the overload without a mapper`() {
        val address = GoldAddress(
            module = ":core",
            fqName = "p.TaskQueries.selectAll",
            kind = "generated.sqldelight-query",
            generated = true,
            file = sq,
            line = 15,
        )
        assertEquals(NodeRef.OfSymbol(plain.id), nodeOf(address))
    }
}
