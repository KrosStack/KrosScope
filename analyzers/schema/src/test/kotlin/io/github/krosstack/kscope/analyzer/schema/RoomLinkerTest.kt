package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.room.RoomLinker
import io.github.krosstack.kscope.analyzer.schema.room.RoomSchemaProducer
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val module = TestRepository.moduleId(":data:settings")
private val sourceSet = TestRepository.sourceSetId(":data:settings", "main")
private const val SOURCE_FILE = "data/settings/src/main/kotlin/Settings.kt"

private fun kotlinSymbol(
    descriptor: SymbolDescriptor,
    kind: String,
    category: SymbolCategory,
    container: Symbol?,
    attributes: Map<String, String>,
): Symbol {
    val name = descriptor.segments.last().name
    return Symbol(
        id = SymbolIdentity.workspace(module, "main", descriptor).toSymbolId(),
        kind = SymbolKind(kind, category),
        name = name,
        qualifiedName = "sample.settings." + descriptor.segments.drop(2).joinToString(".") { it.name },
        signature = name,
        container = container?.id,
        visibility = Visibility.PUBLIC,
        flags = emptySet(),
        origin = SymbolOrigin.Source,
        declaration = SourceLocation(
            ArtifactRef(TestRepository.id, RepoPath(SOURCE_FILE)),
            TextRange(1, 1, 2, 1),
        ),
        nameLocation = null,
        documentation = null,
        scope = Scope(sourceSet = sourceSet),
        evidenceClass = EvidenceClass.STRUCTURAL,
        certainty = Certainty.EXACT,
        attributes = attributes,
    )
}

private fun typeSymbol(name: String, annotations: String, attributes: Map<String, String> = emptyMap()) = kotlinSymbol(
    SymbolDescriptor().namespace("sample").namespace("settings").type(name),
    "kotlin.class",
    SymbolCategory.TYPE,
    null,
    mapOf("annotations" to annotations) + attributes,
)

private fun member(owner: Symbol, name: String, kind: String, attributes: Map<String, String> = emptyMap()): Symbol {
    val descriptor = SymbolDescriptor().namespace("sample").namespace("settings").type(owner.name).let {
        if (kind == "kotlin.function") it.method(name) else it.term(name)
    }
    return kotlinSymbol(descriptor, kind, SymbolCategory.VARIABLE, owner, attributes)
}

private fun uses(from: Symbol, to: Symbol, kind: RelationKind, line: Int) = Relationship(
    from = NodeRef.OfSymbol(from.id),
    kind = kind,
    subKind = null,
    resolution = Resolution.Resolved(NodeRef.OfSymbol(to.id), Certainty.EXACT),
    evidenceClass = EvidenceClass.COMPILER,
    site = SourceLocation(
        ArtifactRef(TestRepository.id, RepoPath(SOURCE_FILE)),
        TextRange(line, 1, line, 10),
    ),
    scope = Scope(sourceSet = sourceSet),
)

class RoomLinkerTest {
    private val workspace = TestWorkspace(
        mapOf(SCHEMA_PATH to Resources.text("room/1.json")),
        listOf(ModuleSpec(":data:settings")),
    )
    private val schemaFacts = workspace.produce(RoomSchemaProducer()).facts()

    private val entity = typeSymbol(
        "UserPreference",
        "androidx.room.Entity",
        mapOf("annotation.Entity.tableName" to "user_preference"),
    )
    private val name = member(entity, "name", "kotlin.property")
    private val value = member(entity, "value", "kotlin.property")
    private val updatedAt = member(
        entity,
        "updatedAt",
        "kotlin.property",
        mapOf("annotation.ColumnInfo.name" to "updated_at"),
    )
    private val dao = typeSymbol("PreferenceDao", "androidx.room.Dao")
    private val observeAll = member(
        dao,
        "observeAll",
        "kotlin.function",
        mapOf("annotation.Query.value" to "SELECT * FROM user_preference ORDER BY name"),
    )
    private val upsert = member(dao, "upsert", "kotlin.function")
    private val database = typeSymbol("SettingsDatabase", "androidx.room.Database")
        .copy(qualifiedName = "sample.tasktide.data.settings.SettingsDatabase")
    private val preferenceDao = member(database, "preferenceDao", "kotlin.function")
    private val compilerEdges = listOf(
        uses(database, entity, RelationKind.REFERENCES, 8),
        uses(preferenceDao, dao, RelationKind.TYPE_USES, 10),
        uses(observeAll, entity, RelationKind.TYPE_USES, 11),
        uses(upsert, entity, RelationKind.TYPE_USES, 17),
    )
    private val symbols = listOf(entity, name, value, updatedAt, dao, observeAll, upsert, database, preferenceDao)

    private fun link(extra: List<Symbol> = emptyList(), edges: List<Relationship> = compilerEdges): List<Relationship> {
        val facts = schemaFacts + (symbols + extra).map { Fact.OfSymbol(it) } + edges.map { Fact.OfRelationship(it) }
        val out = mutableListOf<Fact>()
        RoomLinker().link(FakeGraph(facts, workspace.model, workspace.artifacts)) { out += it.facts }
        return out.relationships()
    }

    private fun Relationship.described(): String = Render.relationship(
        this,
    ).replace(":data:settings@main:", "").replace(":data:settings@.:", "")

    @Test
    fun `a schema entity is bound to the entity class of its table with generated artifact evidence`() {
        val bind = link().single { it.subKind == "schema-entity" }
        assertEquals(
            "BINDS/schema-entity room/schema/sample/tasktide/data/settings/SettingsDatabase/v1#user_preference# " +
                "-> sample/settings/UserPreference# GENERATED_ARTIFACT",
            bind.described(),
        )
        assertEquals(Certainty.EXACT, (bind.resolution as Resolution.Resolved).certainty)
    }

    @Test
    fun `a schema field is bound to the property that its column annotation or its name says`() {
        val binds = link().filter { it.subKind == "schema-field" }.map {
            it.described().substringAfter(
                "#user_preference#",
            )
        }
        assertEquals(
            listOf(
                "name. -> sample/settings/UserPreference#name. GENERATED_ARTIFACT",
                "value. -> sample/settings/UserPreference#value. GENERATED_ARTIFACT",
                "updated_at. -> sample/settings/UserPreference#updatedAt. GENERATED_ARTIFACT",
            ),
            binds,
        )
    }

    @Test
    fun `a table that no entity names gets no binding`() {
        val renamed = entity.copy(attributes = entity.attributes + ("annotation.Entity.tableName" to "other"))
        val facts = schemaFacts + listOf(renamed, dao, database).map { Fact.OfSymbol(it) }
        val out = mutableListOf<Fact>()
        RoomLinker().link(FakeGraph(facts, workspace.model, workspace.artifacts)) { out += it.facts }
        assertEquals(emptyList(), out.relationships().filter { it.subKind == "schema-entity" })
    }

    @Test
    fun `a table without a name in the annotation is bound by the name of the class, with convention evidence`() {
        val plain = entity.copy(
            name = "user_preference",
            qualifiedName = "sample.settings.user_preference",
            attributes = mapOf("annotations" to "androidx.room.Entity"),
        )
        val facts = schemaFacts + listOf(plain, database).map { Fact.OfSymbol(it) }
        val out = mutableListOf<Fact>()
        RoomLinker().link(FakeGraph(facts, workspace.model, workspace.artifacts)) { out += it.facts }
        val bind = out.relationships().single { it.subKind == "schema-entity" }
        assertEquals(EvidenceClass.CONVENTION, bind.evidenceClass)
    }

    @Test
    fun `a database is bound to its entities, its DAOs and its schema file`() {
        val binds = link().filter { it.from == NodeRef.OfSymbol(database.id) }.map { it.described() }
        assertTrue(
            "BINDS/entity sample/settings/SettingsDatabase# -> sample/settings/UserPreference# COMPILER" in binds,
            "$binds",
        )
        assertTrue(
            "BINDS/dao sample/settings/SettingsDatabase# -> sample/settings/PreferenceDao# COMPILER" in binds,
            "$binds",
        )
        val schema = link().single { it.subKind == "exported-schema" }
        assertEquals(EvidenceClass.GENERATED_ARTIFACT, schema.evidenceClass)
        assertEquals(
            SCHEMA_PATH.substringAfterLast('/'),
            Render.node((schema.resolution as Resolution.Resolved).target),
        )
    }

    @Test
    fun `a DAO is bound to the entities its methods use, one binding for each use, at the line of the use`() {
        val binds = link().filter { it.subKind == "entity" && it.from == NodeRef.OfSymbol(dao.id) }
        assertEquals(listOf(11, 17), binds.map { requireNotNull(it.site).range.startLine })
        assertTrue(binds.all { it.evidenceClass == EvidenceClass.COMPILER })
    }

    @Test
    fun `a DAO method is bound to the entity of the table its query names, by the text of the query`() {
        val bind = link().single { it.subKind == "query-table" }
        assertEquals(
            "BINDS/query-table sample/settings/PreferenceDao#observeAll(). -> sample/settings/UserPreference# TEXTUAL",
            bind.described(),
        )
        assertEquals(Certainty.HEURISTIC, (bind.resolution as Resolution.Resolved).certainty)
    }

    @Test
    fun `a graph without Room classes yields nothing`() {
        val out = mutableListOf<Fact>()
        RoomLinker().link(FakeGraph(emptyList(), workspace.model)) { out += it.facts }
        assertEquals(emptyList(), out)
    }
}
