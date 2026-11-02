package io.github.krosstack.kscope.benchmark.score.contract

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals

private fun code(fqName: String, file: String, line: Int) =
    GoldAddress(module = ":core", sourceSet = "main", fqName = fqName, kind = "class", file = file, line = line)

class SqlQueryScorerTest {
    private val sq = "db/src/commonMain/sqldelight/p/Task.sq"
    private val repository = "db/src/commonMain/kotlin/p/Repository.kt"

    private val query = Graphs.symbol(sq, "p.TaskQueries.selectAll", "sqldelight.query", 15, Graphs.type("Task"))
        .ofKind("sqldelight.query")
    private val function = Graphs.symbol(
        "",
        "p.TaskQueries.selectAll",
        "kotlin.function",
        1,
        Graphs.method("TaskQueries", "selectAll", ""),
    ).madeBy("sqldelight")
    private val caller = Graphs.symbol(
        repository,
        "p.Repository.observe",
        "kotlin.function",
        18,
        Graphs.method("Repository", "observe", ""),
    )
    private val graph = coreGraph(
        symbols = listOf(query, function, caller),
        files = listOf(sq, repository),
        relationships = listOf(
            schemaEdge(query, function, RelationKind.GENERATES, EvidenceClass.CONVENTION),
            schemaEdge(caller, function, RelationKind.CALLS, EvidenceClass.COMPILER, repository to 19),
        ),
    )

    @Test
    fun `a call of a generated function is a consumer of the query that generates it`() {
        val (index, resolver) = scoringOf(graph)
        val from = GoldAddress(module = ":core", file = sq, kind = "sq.query", name = "selectAll", line = 15)
        val to = code("p.Repository.observe", repository, 18).copy(kind = "method")
        val gold = schemaGold("q:1", "sq_query_to_consumer", from, to, "generated-artifact")
        val result = SqlQueryScorer(index, resolver).score(listOf(gold))
        assertEquals(1.0, result.score?.recall, "${result.score?.misses}")
        assertEquals(1.0, result.score?.precision, "${result.score?.extras}")
        assertEquals(0, result.evidenceMismatches)
    }
}

class RoomScorerTest {
    private val entityFile = "data/src/main/kotlin/p/Item.kt"
    private val databaseFile = "data/src/main/kotlin/p/Db.kt"
    private val usedFile = "data/src/main/kotlin/p/Repository.kt"

    private fun room(file: String, name: String, line: Int, role: String): Symbol =
        Graphs.symbol(file, "p.$name", "kotlin.class", line, Graphs.type(name))
            .copy(attributes = mapOf("annotations" to "androidx.room.$role"))

    private val entity = room(entityFile, "Item", 5, "Entity")
    private val database = room(databaseFile, "Db", 6, "Database")
    private val dao = room(databaseFile, "ItemDao", 9, "Dao")
    private val user = Graphs.symbol(usedFile, "p.Repository", "kotlin.class", 3, Graphs.type("Repository"))

    private fun binds(from: Symbol, to: Symbol, subKind: String) =
        schemaEdge(from, to, RelationKind.BINDS, EvidenceClass.COMPILER).copy(subKind = subKind)

    private val graph = coreGraph(
        symbols = listOf(entity, database, dao, user),
        files = listOf(entityFile, databaseFile, usedFile),
        relationships = listOf(
            binds(database, entity, "entity"),
            binds(database, dao, "dao"),
            binds(dao, entity, "entity"),
            schemaEdge(user, dao, RelationKind.TYPE_USES, EvidenceClass.COMPILER, usedFile to 4),
            schemaEdge(user, database, RelationKind.IMPORTS, EvidenceClass.COMPILER, usedFile to 1),
            schemaEdge(database, dao, RelationKind.TYPE_USES, EvidenceClass.COMPILER, databaseFile to 10),
        ),
    )

    @Test
    fun `the database and the DAO are listed from the entity as gold lists them`() {
        val (index, resolver) = scoringOf(graph)
        val item = code("p.Item", entityFile, 5)
        val db = code("p.Db", databaseFile, 6)
        val itemDao = code("p.ItemDao", databaseFile, 9)
        val gold = listOf(
            schemaGold("r:1", "room_entity_dao_database", item, db, "compiler"),
            schemaGold("r:2", "room_entity_dao_database", item, itemDao, "compiler"),
            schemaGold("r:3", "room_entity_dao_database", itemDao, db, "compiler"),
        )
        val result = RoomStructureScorer(index, resolver).score(gold)
        assertEquals(1.0, result.score?.recall, "${result.score?.misses}")
        assertEquals(emptyList(), result.score?.extras)
    }

    @Test
    fun `consumers of a component are files outside the Room declarations and each file counts once`() {
        val (index, resolver) = scoringOf(graph)
        val consumer = GoldAddress(file = usedFile, kind = "class", lines = listOf(4))
        val gold = listOf(
            schemaGold("c:1", "room_component_to_consumer", code("p.ItemDao", databaseFile, 9), consumer, "compiler"),
            schemaGold("c:2", "room_component_to_consumer", code("p.Db", databaseFile, 6), consumer, "compiler"),
        )
        val result = RoomConsumerScorer(index, resolver).score(gold)
        assertEquals(1.0, result.score?.recall, "${result.score?.misses}")
        assertEquals(emptyList(), result.score?.extras, "The database file refers to the DAO but declares Room code")
    }
}
