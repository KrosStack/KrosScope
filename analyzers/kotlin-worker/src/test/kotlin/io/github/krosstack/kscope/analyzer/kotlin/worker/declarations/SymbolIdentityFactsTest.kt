package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyze
import io.github.krosstack.kscope.analyzer.kotlin.worker.diagnostics
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolIdentityFactsTest {
    private val shapes = SampleWorkspace.singleModule("shapes", "shapes/main")
    private val overloads = SampleWorkspace.singleModule("overloads", "overloads/main")

    private fun List<Symbol>.byQualifiedName(name: String) = filter { it.qualifiedName == name }

    @Test
    fun `symbol ids follow the identity layout of module, source set and descriptor`() {
        val symbol = analyze(shapes).symbols().single { it.qualifiedName == "shapes.Outer.Inner.read" }
        assertEquals("kscope gradle sample//:shapes main shapes/Outer#Inner#read().", symbol.id.value)
    }

    @Test
    fun `every symbol is structural, exact and owned by its source set`() {
        val symbols = analyze(shapes).symbols()
        assertTrue(symbols.isNotEmpty())
        assertTrue(symbols.all { it.evidenceClass == EvidenceClass.STRUCTURAL && it.certainty == Certainty.EXACT })
        assertTrue(symbols.all { it.origin == SymbolOrigin.Source })
        assertTrue(symbols.all { it.scope.sourceSet?.value == "sample//:shapes@main" })
    }

    @Test
    fun `members point at their container and top level declarations have none`() {
        val symbols = analyze(shapes).symbols()
        val outer = symbols.single { it.qualifiedName == "shapes.Outer" }
        val companion = symbols.single { it.qualifiedName == "shapes.Outer.Companion" }
        val create = symbols.single { it.qualifiedName == "shapes.Outer.Companion.create" }
        assertEquals(null, outer.container)
        assertEquals(outer.id, companion.container)
        assertEquals(companion.id, create.container)
    }

    @Test
    fun `local declarations and anonymous objects are not symbols`() {
        val names = analyze(shapes).symbols().mapNotNull { it.qualifiedName }
        assertTrue(names.none { it.endsWith("Hidden") || it.endsWith("inner") || it.endsWith("nested") })
    }

    @Test
    fun `same named overloads get distinct ids`() {
        val renders = analyze(overloads).symbols().byQualifiedName("over.render")
        val parameterLists = renders.map { it.id.value.substringAfter("render(").substringBefore(").") }.toSet()
        val expected = setOf("Array", "Function", "Function,Int", "Int", "KMap", "List", "Map", "String", "vararg:Int")
        assertEquals(expected, parameterLists)
    }

    @Test
    fun `function types share a simple name, so such overloads collide with a diagnostic`() {
        val collisions = analyze(overloads).diagnostics().filter { it.code.value == "identity.collision" }
        assertEquals(2, collisions.size)
        assertTrue(collisions.all { it.severity == DiagnosticSeverity.WARNING && it.location != null })
    }

    @Test
    fun `extension properties with the same name on different receivers have different ids`() {
        val symbols = analyze(overloads).symbols().byQualifiedName("over.shoutLength")
        val ids = symbols.map { it.id.value.substringAfter(" main ") }
        assertEquals(listOf("over/shoutLength(this:Int).", "over/shoutLength(this:String)."), ids.sorted())
    }

    @Test
    fun `an extension property differs from a member property of the same name`() {
        val ids = analyze(overloads).symbols().filter { it.name == "self" || it.name == "value" }.map { it.id.value }
        assertTrue(ids.any { it.endsWith("over/Box#self(this:T).") })
        assertTrue(ids.any { it.endsWith("over/Box#value.") })
    }

    @Test
    fun `constructors are symbols with the init name and their parameter types`() {
        val constructors = analyze(SampleWorkspace.singleModule("constructors", "constructors/main"))
            .symbols().filter { it.kind.id == "kotlin.constructor" }
        val mixed = constructors.byQualifiedName("ctor.Mixed.<init>").map { it.id.value.substringAfter("Mixed#") }
        val expected = listOf("`<init>`().", "`<init>`(Int).", "`<init>`(Int,Int).", "`<init>`(String,vararg:Int).")
        assertEquals(expected, mixed.sorted())
        assertTrue(constructors.none { it.qualifiedName == "ctor.Plain.<init>" })
    }
}
