package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.allRelationships
import io.github.krosstack.kscope.service.assertCompilerExact
import io.github.krosstack.kscope.service.danglingTargets
import io.github.krosstack.kscope.service.edgesInto
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scans an Android library with a Room entity, DAO and database, the schema JSON that Room exports for it and a
 * repository. The linker binds the schema to the code, and the code to each other, from what the analyzers found.
 */
class RoomIntegrationTest {
    private val sample = SampleWorkspace("room")
    private val schemaFile = "store/schemas/sample.room.store.StoreDatabase/1.json"
    private val pkg = "sample.room.store"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    private fun bindings(service: KrosScope, subKind: String): List<Relationship> =
        service.graph().allRelationships().filter { it.kind == RelationKind.BINDS && it.subKind == subKind }

    private fun nameOf(service: KrosScope, node: NodeRef): String? =
        (node as? NodeRef.OfSymbol)?.let { service.graph().symbol(it.id)?.qualifiedName }

    private fun Relationship.pair(service: KrosScope): Pair<String?, String?> =
        nameOf(service, from) to nameOf(service, (resolution as Resolution.Resolved).target)

    @Test
    fun `the schema entity and its fields are bound to the entity class and its properties`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            val entity = bindings(service, "schema-entity").single()
            assertEquals("$pkg.StoreDatabase.items" to "$pkg.Item", entity.pair(service))
            assertEquals(EvidenceClass.GENERATED_ARTIFACT, entity.evidenceClass)
            val fields = bindings(service, "schema-field").map { it.pair(service) }
            val table = "$pkg.StoreDatabase.items"
            assertEquals(setOf("$table.id" to "$pkg.Item.id", "$table.item_name" to "$pkg.Item.name"), fields.toSet())
        }
    }

    @Test
    fun `the database is bound to its entity, its DAO and its schema file with the evidence of the compiler`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val entity = bindings(service, "entity").map { it.pair(service) to it.evidenceClass }
            assertTrue(("$pkg.StoreDatabase" to "$pkg.Item") to EvidenceClass.COMPILER in entity, "$entity")
            assertTrue(("$pkg.ItemDao" to "$pkg.Item") to EvidenceClass.COMPILER in entity, "$entity")
            val dao = bindings(service, "dao").single()
            assertEquals("$pkg.StoreDatabase" to "$pkg.ItemDao", dao.pair(service))
            val schema = bindings(service, "exported-schema").single()
            assertEquals(EvidenceClass.GENERATED_ARTIFACT, schema.evidenceClass)
            val target = (schema.resolution as Resolution.Resolved).target as NodeRef.OfArtifact
            assertEquals(schemaFile, target.ref.path.value)
        }
    }

    @Test
    fun `a DAO method is bound to the table of its query by the text, heuristically`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val query = bindings(service, "query-table").single()
            assertEquals("$pkg.ItemDao.find" to "$pkg.Item", query.pair(service))
            assertEquals(EvidenceClass.TEXTUAL, query.evidenceClass)
        }
    }

    @Test
    fun `the repository reaches the database and the DAO with compiler evidence and no target is missing`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val repository = "store/src/main/kotlin/sample/room/store/ItemRepository.kt"
            assertCompilerExact(graph.edgesInto(repository, "$pkg.StoreDatabase.itemDao"), "the DAO getter")
            assertCompilerExact(graph.edgesInto(repository, "$pkg.ItemDao.find"), "the DAO method")
            assertEquals(emptyList(), graph.danglingTargets())
        }
    }
}
