package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.room.RoomLinker
import io.github.krosstack.kscope.analyzer.schema.room.RoomSchemaProducer
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals

private fun classIn(module: String, name: String, annotation: String, attributes: Map<String, String> = emptyMap()) =
    Samples.symbol.copy(
        id = SymbolIdentity.workspace(
            TestRepository.moduleId(module),
            "main",
            SymbolDescriptor().namespace("p").type(name),
        ).toSymbolId(),
        kind = SymbolKind("kotlin.class", SymbolCategory.TYPE),
        name = name,
        qualifiedName = "p.$name",
        container = null,
        scope = Scope(sourceSet = TestRepository.sourceSetId(module, "main")),
        attributes = mapOf("annotations" to annotation) + attributes,
    )

class RoomModulesTest {
    private val first = Resources.text("room/1.json")
    private val workspace = TestWorkspace(
        mapOf(
            "a/schemas/p.Database/1.json" to first,
            "b/schemas/p.Database/2.json" to first.replace("\"version\": 1", "\"version\": 2"),
        ),
        listOf(ModuleSpec(":a"), ModuleSpec(":b")),
    )
    private val classes: List<Symbol> = listOf(":a", ":b").flatMap { module ->
        val table = mapOf("annotation.Entity.tableName" to "user_preference")
        listOf(
            classIn(module, "Database", "androidx.room.Database"),
            classIn(module, "Preference", "androidx.room.Entity", table),
        )
    }

    private val relationships = link(workspace, classes, emptyList())

    private fun link(workspace: TestWorkspace, symbols: List<Symbol>, edges: List<Relationship>): List<Relationship> {
        val facts = workspace.produce(RoomSchemaProducer()).facts() + symbols.map { Fact.OfSymbol(it) } +
            edges.map { Fact.OfRelationship(it) }
        val out = mutableListOf<Fact>()
        RoomLinker().link(FakeGraph(facts, workspace.model, workspace.artifacts)) { out += it.facts }
        return out.relationships()
    }

    private fun bindings(subKind: String, from: List<Relationship> = relationships): Map<String, String> =
        from.filter { it.subKind == subKind }
            .associate { Render.node(it.from) to Render.node((it.resolution as Resolution.Resolved).target) }

    @Test
    fun `the databases of two modules with one name keep the schema of their own module`() {
        assertEquals(
            mapOf(":a@main:p/Database#" to "1.json", ":b@main:p/Database#" to "2.json"),
            bindings("exported-schema"),
        )
    }

    @Test
    fun `the latest version counts for each module and its entities bind to the classes of that module`() {
        assertEquals(
            mapOf(
                ":a@.:room/schema/p/Database/v1#user_preference#" to ":a@main:p/Preference#",
                ":b@.:room/schema/p/Database/v2#user_preference#" to ":b@main:p/Preference#",
            ),
            bindings("schema-entity"),
        )
    }

    @Test
    fun `an entity that the database lists wins over an unrelated entity of its module with the same table`() {
        val app = TestWorkspace(
            mapOf("app/schemas/p.Database/1.json" to first),
            listOf(ModuleSpec(":app"), ModuleSpec(":lib")),
        )
        val table = mapOf("annotation.Entity.tableName" to "user_preference")
        val database = classIn(":app", "Database", "androidx.room.Database")
        val unrelated = classIn(":app", "Unrelated", "androidx.room.Entity", table)
        val listed = classIn(":lib", "Preference", "androidx.room.Entity", table)
        val listing = Samples.relationship.copy(
            from = NodeRef.OfSymbol(database.id),
            kind = RelationKind.REFERENCES,
            subKind = null,
            resolution = Resolution.Resolved(NodeRef.OfSymbol(listed.id), Certainty.EXACT),
        )
        val linked = link(app, listOf(database, unrelated, listed), listOf(listing))
        assertEquals(
            mapOf(":app@.:room/schema/p/Database/v1#user_preference#" to ":lib@main:p/Preference#"),
            bindings("schema-entity", linked),
        )
        val entityBinding = linked.single { it.subKind == "schema-entity" }
        assertEquals(Certainty.EXACT, (entityBinding.resolution as Resolution.Resolved).certainty)
    }
}
