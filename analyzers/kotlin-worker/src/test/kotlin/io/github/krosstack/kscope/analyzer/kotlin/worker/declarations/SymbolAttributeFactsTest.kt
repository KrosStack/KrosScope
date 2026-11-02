package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyze
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolAttributeFactsTest {
    private fun symbolsOf(name: String, directory: String): List<Symbol> =
        analyze(SampleWorkspace.singleModule(name, directory)).symbols()

    private fun List<Symbol>.named(qualifiedName: String) = single { it.qualifiedName == qualifiedName }

    @Test
    fun `only functions annotated with test and the types containing them carry the test flag`() {
        val flagged = symbolsOf("tests", "tests/test").filter { SymbolFlag.TEST in it.flags }
        val expected = listOf(
            "tests.AliasedTest",
            "tests.AliasedTest.viaAlias",
            "tests.CalculatorTest",
            "tests.CalculatorTest.adds",
            "tests.CalculatorTest.subtracts",
            "tests.JavaTest",
            "tests.JavaTest.withTimeout",
            "tests.JavaTest.works",
            "tests.NestedTests",
            "tests.NestedTests.Deep",
            "tests.NestedTests.Deep.works",
            "tests.OtherTest",
            "tests.OtherTest.withArguments",
            "tests.StarImportTest",
            "tests.StarImportTest.viaStarImport",
        )
        assertEquals(expected, flagged.mapNotNull { it.qualifiedName }.sorted())
    }

    @Test
    fun `an annotation named test that is not a test framework annotation does not flag a test`() {
        val shadowed = symbolsOf("tests", "tests/test").named("shadowed.Shadowed.notATest")
        assertTrue(SymbolFlag.TEST !in shadowed.flags)
        assertEquals("shadowed.Test", shadowed.attributes["annotations"])
    }

    @Test
    fun `annotations are recorded as sorted names and literal arguments`() {
        val symbols = symbolsOf("shapes", "shapes/main")
        val annotated = symbols.named("shapes.Annotated")
        assertEquals("shapes.Marker", annotated.attributes["annotations"])
        assertEquals("2", annotated.attributes["annotation.Marker.level"])
        assertEquals("top", annotated.attributes["annotation.Marker.label"])
        val legacy = symbols.named("shapes.Annotated.legacy")
        assertEquals("kotlin.Deprecated kotlin.Suppress", legacy.attributes["annotations"])
        assertEquals("old", legacy.attributes["annotation.Deprecated.value"])
        assertEquals(null, legacy.attributes["annotation.Suppress.value"])
    }

    @Test
    fun `a single positional annotation argument is recorded as the value parameter`() {
        val marked = symbolsOf("shapes", "shapes/main").named("shapes.Annotated.marked")
        assertEquals("3", marked.attributes["annotation.Marker.value"])
    }

    @Test
    fun `visibility follows the modifiers and Java package private maps to internal`() {
        val flags = symbolsOf("flags", "flags/main")
        assertEquals(Visibility.INTERNAL, flags.named("flags.Base.hidden").visibility)
        assertEquals(Visibility.PRIVATE, flags.named("flags.Base.secret").visibility)
        assertEquals(Visibility.PROTECTED, flags.named("flags.Base.inherited").visibility)
        assertEquals(Visibility.PUBLIC, flags.named("flags.Base.required").visibility)
        val java = symbolsOf("mixed", "mixed/main")
        assertEquals(Visibility.INTERNAL, java.named("mixed.legacy.Base").visibility)
        assertEquals(Visibility.PUBLIC, java.named("mixed.legacy.JavaSupport.Listener.onEvent").visibility)
    }

    @Test
    fun `Kotlin flags follow the modifiers`() {
        val flags = symbolsOf("flags", "flags/main")
        assertEquals(setOf(SymbolFlag.ABSTRACT), flags.named("flags.Base.required").flags)
        assertEquals(setOf(SymbolFlag.SUSPEND), flags.named("flags.Base.waiting").flags)
        assertEquals(setOf(SymbolFlag.EXTERNAL), flags.named("flags.Base.native").flags)
        assertEquals(setOf(SymbolFlag.ABSTRACT), flags.named("flags.Contract.mandatory").flags)
        assertEquals(emptySet(), flags.named("flags.Contract.provided").flags)
    }

    @Test
    fun `Java flags follow the modifiers and annotations`() {
        val java = symbolsOf("mixed", "mixed/main")
        assertEquals(setOf(SymbolFlag.EXTERNAL, SymbolFlag.STATIC), java.named("mixed.legacy.JavaSupport.fast").flags)
        val shouts = java.filter { it.qualifiedName == "mixed.legacy.JavaSupport.shout" }
        val overriding = shouts.single { SymbolFlag.OVERRIDE in it.flags }
        assertEquals(setOf(SymbolFlag.OVERRIDE, SymbolFlag.OPEN), overriding.flags)
    }

    @Test
    fun `documentation holds the first sentence only`() {
        val symbols = symbolsOf("docs", "docs/main")
        assertEquals("Loads the cached tasks.", symbols.named("docs.TaskLoader").documentation)
        assertEquals("Finds the task by id.", symbols.named("docs.Documented.find").documentation)
        assertEquals(null, symbols.named("docs.TaskLoader.tagOnly").documentation)
        assertEquals(null, symbols.named("docs.TaskLoader.plain").documentation)
    }

    @Test
    fun `a Java method with a deprecated javadoc tag is flagged deprecated`() {
        val find = symbolsOf("docs", "docs/main").named("docs.Documented.find")
        assertTrue(SymbolFlag.DEPRECATED in find.flags)
    }
}
