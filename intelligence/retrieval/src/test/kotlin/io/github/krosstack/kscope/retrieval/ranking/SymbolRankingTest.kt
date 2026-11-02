package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.retrieval.RetrievalIndex
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.retrieval.artifact
import io.github.krosstack.kscope.retrieval.relationship
import io.github.krosstack.kscope.spi.search.SearchHit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SymbolRankingTest {
    @Test
    fun `task symbol results include source declarations reached through graph relationships`() {
        val source = artifact("source/Consumer.kt")
        val schema = artifact("schema/Entity")
        val consumer = retrievalSymbol("Consumer", source)
        val entity = retrievalSymbol("Entity", schema, SymbolCategory.SCHEMA)
        val use = relationship(NodeRef.OfSymbol(consumer.id), NodeRef.OfSymbol(entity.id), RelationKind.TYPE_USES)
        val fixture = SemanticFixture(listOf(source, schema), listOf(consumer, entity), listOf(use))
        val lexical = SearchHit(NodeRef.OfSymbol(consumer.id), 1.0, listOf("consumer"), emptyMap())
        val retrieval = TaskRetrieval(fixture, RetrievalIndex(setOf("consumer"), listOf(lexical)))

        val result = retrieval.analyze("consumer")

        assertTrue(result.symbols.any { it.symbol.id == entity.id && it.score > 0.0 })
    }

    @Test
    fun `generated symbol hits rank the source contract declaration`() {
        val schema = artifact("schema/Entity")
        val entity = retrievalSymbol("Entity", schema, SymbolCategory.SCHEMA)
        val generated = retrievalSymbol(
            "GeneratedEntity",
            origin = SymbolOrigin.Generated("test.generator", NodeRef.OfSymbol(entity.id)),
        )
        val generation = relationship(
            NodeRef.OfSymbol(entity.id),
            NodeRef.OfSymbol(generated.id),
            RelationKind.GENERATES,
        )
        val fixture = SemanticFixture(listOf(schema), listOf(entity, generated), listOf(generation))
        val lexical = SearchHit(NodeRef.OfSymbol(generated.id), 1.0, listOf("entity"), emptyMap())
        val retrieval = TaskRetrieval(fixture, RetrievalIndex(setOf("entity"), listOf(lexical)))

        val result = retrieval.analyze("entity")

        assertEquals(listOf(entity.id), result.symbols.map { it.symbol.id })
        assertEquals(
            listOf(generated.id),
            retrieval.analyze("entity", RetrievalStage.LEXICAL).symbols.map { it.symbol.id },
        )
    }

    @Test
    fun `generation edges identify source declarations when generated origins point to artifacts`() {
        val schema = artifact("schema/Entity")
        val entity = retrievalSymbol("Entity", schema, SymbolCategory.SCHEMA)
        val generated = retrievalSymbol(
            "GeneratedEntity",
            origin = SymbolOrigin.Generated("test.generator", NodeRef.OfArtifact(schema.ref)),
        )
        val generation = relationship(
            NodeRef.OfSymbol(entity.id),
            NodeRef.OfSymbol(generated.id),
            RelationKind.GENERATES,
        )
        val fixture = SemanticFixture(listOf(schema), listOf(entity, generated), listOf(generation))
        val lexical = SearchHit(NodeRef.OfSymbol(generated.id), 1.0, listOf("entity"), emptyMap())
        val retrieval = TaskRetrieval(fixture, RetrievalIndex(setOf("entity"), listOf(lexical)))

        val result = retrieval.analyze("entity")

        assertEquals(listOf(entity.id), result.symbols.map { it.symbol.id })
    }
}
