package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyze
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.spi.worker.WorkerModule
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test as JupiterTest

class LibraryModulesTest {
    private val tests = SampleWorkspace.singleModule("tests", "tests/test")

    private fun jarOf(type: Class<*>): String = Path.of(type.protectionDomain.codeSource.location.toURI()).toString()

    private fun viaStarImport(module: WorkerModule): Symbol =
        analyze(module).symbols().single { it.qualifiedName == "tests.StarImportTest.viaStarImport" }

    @Test
    fun `without a classpath an annotation named by a star import stays as written but is still a test`() {
        val symbol = viaStarImport(tests)
        assertEquals("Test", symbol.attributes["annotations"])
        assertTrue(SymbolFlag.TEST in symbol.flags)
    }

    @Test
    fun `a classpath entry lets the compiler qualify an annotation that only a star import names`() {
        val symbol = viaStarImport(tests.copy(classpath = listOf(jarOf(JupiterTest::class.java))))
        assertEquals("org.junit.jupiter.api.Test", symbol.attributes["annotations"])
        assertTrue(SymbolFlag.TEST in symbol.flags)
    }

    @Test
    fun `classpath entries that do not exist or are not paths are ignored`() {
        val symbol = viaStarImport(tests.copy(classpath = listOf("/nowhere/missing.jar", "\u0000bad")))
        assertEquals("Test", symbol.attributes["annotations"])
    }

    @Test
    fun `the defaults let the compiler qualify standard library annotations without any classpath`() {
        val legacy = analyze(SampleWorkspace.singleModule("shapes", "shapes/main")).symbols()
            .single { it.qualifiedName == "shapes.Annotated.legacy" }
        assertEquals("kotlin.Deprecated kotlin.Suppress", legacy.attributes["annotations"])
    }
}
