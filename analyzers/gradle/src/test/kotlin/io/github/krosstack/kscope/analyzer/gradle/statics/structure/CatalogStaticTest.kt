package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.edgesOf
import io.github.krosstack.kscope.analyzer.gradle.statics.module
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolCategory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogStaticTest {
    private val tree = StaticWorkspace("catalog-kotlin")
    private val provided = tree.provided()
    private val facts = CatalogFacts(provided)

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `every catalog entry is a build symbol of its section`() {
        assertEquals(
            mapOf("version" to 6, "library" to 6, "bundle" to 1, "plugin" to 2),
            facts.symbols.groupingBy { it.kind.id.substringAfterLast('.') }.eachCount(),
        )
        assertEquals(setOf(SymbolCategory.BUILD), facts.symbols.map { it.kind.category }.toSet())
        assertEquals(setOf(Certainty.EXACT), facts.symbols.map { it.certainty }.toSet())
    }

    @Test
    fun `symbols are identified by the build root, the catalog, the section and the normalized alias`() {
        val coroutines = facts.symbol("library", "kotlinx.coroutines.core")
        assertEquals(
            "kscope gradle tasktide// . gradle/catalog/libs/libraries/`kotlinx.coroutines.core`.",
            coroutines.id.value,
        )
        assertIs<ParsedSymbolId.Valid>(coroutines.id.identity())
        assertEquals("libs.kotlinx.coroutines.core", coroutines.qualifiedName)
        assertEquals("libs.plugins.kotlin.jvm", facts.symbol("plugin", "kotlin.jvm").qualifiedName)
        assertEquals("libs.bundles.testing", facts.symbol("bundle", "testing").qualifiedName)
        assertEquals("libs.versions.compose.bom", facts.symbol("version", "compose.bom").qualifiedName)
    }

    @Test
    fun `the declaration of a symbol is its entry in the catalog`() {
        val junit = facts.symbol("library", "junit")
        assertEquals("gradle/libs.versions.toml", junit.declaration?.artifact?.path?.value)
        assertEquals(13, junit.declaration?.range?.startLine)
        assertEquals(13, junit.declaration?.range?.endLine)
        assertEquals("junit:junit:4.13.2", junit.signature)
        assertEquals(1, junit.nameLocation?.range?.startColumn)
        assertEquals(6, junit.nameLocation?.range?.endColumn)
    }

    @Test
    fun `plugin aliases give the plugin ids and libraries give coordinates with versions`() {
        val app = provided.module(":app")
        assertEquals(listOf("com.google.devtools.ksp", "org.jetbrains.kotlin.jvm"), app.plugins)
        assertEquals("androidx.room:room-compiler", app.attributes["ksp.processors"])
        val coroutines = provided.edgesOf(":app").single { it.site?.range?.startLine == 7 }
        val target = NodeRef.OfExternal("maven:org.jetbrains.kotlinx:kotlinx-coroutines-core")
        assertEquals(Resolution.Resolved(target, Certainty.EXACT), coroutines.resolution)
        assertEquals("1.11.0", coroutines.attributes["version"])
    }

    @Test
    fun `bundles expand to their libraries`() {
        val testing = provided.edgesOf(":app").filter { it.site?.range?.startLine == 8 }
        assertEquals(
            listOf("maven:junit:junit", "maven:org.jetbrains.kotlin:kotlin-test"),
            testing.map { ((it.resolution as Resolution.Resolved).target as NodeRef.OfExternal).coordinate }.sorted(),
        )
    }
}
