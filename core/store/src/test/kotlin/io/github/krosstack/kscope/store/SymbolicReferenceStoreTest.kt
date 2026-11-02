package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.model.SymbolicReference
import kotlin.test.Test
import kotlin.test.assertEquals

class SymbolicReferenceStoreTest {
    private val resource = Samples.symbolicReference
    private val color = resource.copy(key = "color/brand")
    private val klass = resource.copy(namespace = "jvm.class", key = "sample.ui.PriorityView", subKind = null)

    private fun batchOf(unit: String, vararg references: SymbolicReference) =
        Builders.batch(unit, references.map { Fact.OfSymbolicReference(it) })

    @Test
    fun `symbolic references come back in canonical order across units`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(batchOf("b", klass, resource))
            store.replace(batchOf("a", color))
            assertEquals(listOf(color, resource, klass).sortedBy { canonicalKey(it) }, store.symbolicReferences())
        }
    }

    @Test
    fun `a namespace filter returns the references of that namespace only`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(batchOf("a", resource, color, klass))
            assertEquals(setOf(resource, color), store.symbolicReferences("android.resource").toSet())
            assertEquals(listOf(klass), store.symbolicReferences("jvm.class"))
            assertEquals(emptyList(), store.symbolicReferences("unknown"))
        }
    }

    @Test
    fun `replacing a unit replaces its references and removing it drops them`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(batchOf("a", resource, color))
            store.replace(batchOf("a", klass))
            assertEquals(listOf(klass), store.symbolicReferences())
            store.remove(Builders.producer, OwnershipUnit(OwnershipKind.ARTIFACT, "a"))
            assertEquals(emptyList(), store.symbolicReferences())
        }
    }

    @Test
    fun `references are not relationships and do not count in the coverage`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(batchOf("a", resource))
            assertEquals(emptyList(), store.coverage().cells)
        }
    }

    private fun canonicalKey(reference: SymbolicReference) =
        KscopeJson.encodeToString(Fact.serializer(), Fact.OfSymbolicReference(reference))
}
