package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.RelationSamples
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A stub stands in for generated code that a generator will describe itself, so the id that a reference to the stub
 * gets must be the id that the generator would give the same declaration in the same module and source set.
 */
class SyntheticStubRelationshipsTest {
    private val batches by lazy { analyzeAll(listOf(RelationSamples.withStubs), RelationSamples::isStubSymbol) }
    private val edges: List<String> by lazy { Edges("gen@main ").of(batches) }
    private val module = ModuleId("sample//:gen")
    private val stubs = SymbolDescriptor().namespace("rel").namespace("stubs")

    private fun idOf(descriptor: SymbolDescriptor): String =
        SymbolIdentity.workspace(module, "main", descriptor).toSymbolId().value

    private fun targets(fromClass: String): Set<String> = batches.relationships()
        .filter { (it.from as? NodeRef.OfSymbol)?.id?.value?.contains(" rel/stubs/$fromClass#") == true }
        .mapNotNull { ((it.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id?.value }.toSet()

    private val resource = idOf(stubs.type("R").type("string").term("app_name"))
    private val debugFlag = idOf(stubs.type("BuildConfig").term("DEBUG"))
    private val asInterface = idOf(stubs.type("IFoo").type("Stub").method("asInterface", "Object"))
    private val generated = idOf(stubs.type("Generated").term("value"))

    @Test
    fun `Java stubs are reached from Kotlin with the id a generator would give them`() {
        assertTrue(targets("KotlinUses").containsAll(listOf(resource, debugFlag, asInterface)))
    }

    @Test
    fun `Java stubs are reached from Java with the same ids`() {
        assertTrue(targets("JavaUses").containsAll(listOf(resource, debugFlag, asInterface)))
    }

    @Test
    fun `a Kotlin stub is reached from Kotlin and from Java through its light class`() {
        assertTrue(generated in targets("KotlinUses"))
        assertTrue(generated in targets("JavaUses"))
    }

    @Test
    fun `references to stubs are exact and the stubs themselves are not symbols of the batch`() {
        Edges.assertHas(
            edges,
            "rel/stubs/KotlinUses#resource(). references/read -> rel/stubs/R#string#app_name.",
            "rel/stubs/JavaUses#resource(). references/read -> rel/stubs/R#string#app_name.",
            "rel/stubs/KotlinUses#remote(Any). calls -> rel/stubs/IFoo#Stub#asInterface(Object).",
            "rel/stubs/JavaUses#remote(Object). calls -> rel/stubs/IFoo#Stub#asInterface(Object).",
            "rel/stubs/JavaUses#generated(). calls -> rel/stubs/Generated#value.",
        )
        assertTrue(batches.symbols().none { RelationSamples.isStubSymbol(it.id) })
        assertEquals(setOf("rel/stubs/KotlinUses#", "rel/stubs/JavaUses#"), stubUsers())
    }

    private fun stubUsers(): Set<String> = batches.symbols().filter { it.container == null }
        .map { Edges.text(it.id).substringAfter("gen@main ") }.toSet()
}
