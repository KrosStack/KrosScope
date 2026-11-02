package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.benchmark.score.ClassScore
import kotlin.test.Test
import kotlin.test.assertEquals

/** The relationship classes of the schema artifacts: Wire messages, SQLDelight queries and tables, and Room. */
internal object SchemaConformance {
    val CLASSES = setOf(
        "proto_message_to_consumer",
        "sq_query_to_consumer",
        "sq_table_to_consumer",
        "room_entity_dao_database",
        "room_component_to_consumer",
    )

    fun classOf(result: ConformanceResult, name: String): ClassScore =
        result.relationshipClasses.single { it.name == name }
}

private fun ClassScore.part(label: String) = parts.single { it.label == label }

/** What the Wire and SQLDelight stubs and the Room linker achieve on the reference workspace without a classpath. */
class SchemaConformanceTest {
    private val result = ConformanceFixture.default

    @Test
    fun `every proto message and enum reaches the files that name it and nothing is made up`() {
        val proto = SchemaConformance.classOf(result, "proto_message_to_consumer")
        val named = proto.part("named")
        assertEquals(1.0, named.score.recall, "${named.score.misses}")
        assertEquals(1.0, named.score.precision, "${named.score.extras}")
        assertEquals(14, named.score.expected)
        assertEquals(emptyList(), proto.unmapped)
        assertEquals(0, proto.evidenceMismatches)
    }

    @Test
    fun `the consumers that use a proto type without naming it are reported apart and all of them are found`() {
        val inferred = SchemaConformance.classOf(result, "proto_message_to_consumer").part("inferred-type")
        assertEquals(3, inferred.score.expected)
        assertEquals(1.0, inferred.score.recall, "${inferred.score.misses}")
        assertEquals(1.0, inferred.score.precision, "${inferred.score.extras}")
    }

    @Test
    fun `every query reaches the repository method that calls it`() {
        val queries = SchemaConformance.classOf(result, "sq_query_to_consumer")
        assertEquals(14, queries.goldCount)
        assertEquals(emptyList(), queries.score?.misses)
        assertEquals(emptyList(), queries.score?.extras)
        assertEquals(0, queries.evidenceMismatches)
    }

    @Test
    fun `every table reaches the files that use its row class, named or inferred`() {
        val tables = SchemaConformance.classOf(result, "sq_table_to_consumer")
        assertEquals(emptyList(), tables.score?.misses)
        assertEquals(emptyList(), tables.score?.extras)
        assertEquals(1, tables.part("named").score.expected)
        assertEquals(3, tables.part("inferred-type").score.expected)
    }

    @Test
    fun `the Room entity, DAO, database and schema file are bound and their consumers found`() {
        listOf("room_entity_dao_database", "room_component_to_consumer").forEach { name ->
            val room = SchemaConformance.classOf(result, name)
            assertEquals(emptyList(), room.score?.misses, name)
            assertEquals(emptyList(), room.score?.extras, name)
            assertEquals(0, room.evidenceMismatches, name)
            assertEquals(emptyList(), room.unmapped, name)
        }
    }

    @Test
    fun `the gold edges to Wire and SQLDelight surfaces in the closed-world files are scored and found`() {
        val files = result.closedWorld.files.associateBy { it.file.substringAfterLast('/') }
        val mapper = files.getValue("TaskDtoMapper.kt")
        assertEquals(emptyList(), mapper.deferred)
        assertEquals(1.0, mapper.score.recall, "${mapper.score.misses}")
        assertEquals(1.0, mapper.score.precision, "${mapper.score.extras}")
        val repository = files.getValue("TaskRepository.kt")
        assertEquals(emptyList(), repository.deferred)
        assertEquals(1.0, repository.score.precision, "${repository.score.extras}")
        assertEquals(emptyList(), repository.unmapped)
    }
}
