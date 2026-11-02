package io.github.krosstack.kscope.analyzer.android.rclass

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.Golden
import io.github.krosstack.kscope.analyzer.android.Render
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RClassSymbolsTest : RClassTestBase() {

    @Test
    fun `symbols are generated with the qualified names java sees and a source of truth`() {
        val symbols = run().facts.filterIsInstance<Fact.OfSymbol>().map { it.value }
        val field = symbols.single { it.qualifiedName == "sample.droid.app.R.string.app_name" }
        assertEquals("app_name", field.name)
        assertEquals("java.field", field.kind.id)
        assertEquals(SymbolCategory.VARIABLE, field.kind.category)
        assertEquals("public static final int app_name", field.signature)
        assertEquals(Visibility.PUBLIC, field.visibility)
        assertEquals(setOf(SymbolFlag.STATIC), field.flags)
        assertEquals(EvidenceClass.CONVENTION, field.evidenceClass)
        assertEquals(Certainty.EXACT, field.certainty)
        val origin = field.origin as SymbolOrigin.Generated
        assertEquals("android.r", origin.generator)
        assertTrue(Render.node(assertNotNull(origin.sourceOfTruth)).startsWith(":app@"))
        assertNull(field.declaration)
        assertEquals("kscope gradle repo//:app main sample/droid/app/R#string#app_name.", field.id.value)
        val root = symbols.single { it.qualifiedName == "sample.droid.app.R" }
        assertEquals("java.class", root.kind.id)
        assertNull(root.container)
        val type = symbols.single { it.qualifiedName == "sample.droid.app.R.string" }
        assertEquals(root.id, type.container)
        assertEquals(type.id, field.container)
    }

    @Test
    fun `each resource generates its field with convention evidence, one edge per definition`() {
        val edges = run().facts.filterIsInstance<Fact.OfRelationship>().map { it.value }
            .filter { it.kind == RelationKind.GENERATES }
        val appName = edges.filter {
            Render.node(
                it.from,
            ).let { from -> from.startsWith(":app@") && from.endsWith("android/res/string/app_name.") }
        }
        assertEquals(
            listOf(":app@debug:android/res/string/app_name.", ":app@main:android/res/string/app_name."),
            appName.map { Render.node(it.from) }.sorted(),
        )
        assertTrue(edges.all { it.evidenceClass == EvidenceClass.CONVENTION })
        assertTrue(edges.all { (it.resolution as Resolution.Resolved).certainty == Certainty.EXACT })
        assertTrue(edges.all { (it.resolution as Resolution.Resolved).target is NodeRef.OfSymbol })
        assertEquals(1, appName.map { (it.resolution as Resolution.Resolved).target }.distinct().size)
    }

    @Test
    fun `a module without a namespace or with an invalid one gets a note and no class`() {
        val result = run(SampleTree("droid", DroidModel.standard(appNamespace = null)))
        assertTrue(result.sources.none { it.virtualPath.contains("/app/") })
        val notes = result.facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value.code.value }
        assertEquals(listOf("android.r.namespace-unknown"), notes)
        val invalid = run(SampleTree("droid", DroidModel.standard(appNamespace = "not a package")))
        assertTrue(invalid.sources.none { it.virtualPath.contains("/app/") })
    }

    @Test
    fun `the output does not depend on how often it is made`() {
        val first = SampleTree("droid").synthesize(synthesizer).first
        assertEquals(Golden.facts(first), Golden.facts(SampleTree("droid").synthesize(synthesizer).first))
        assertEquals(OwnershipKind.WORKSPACE, synthesizer.descriptor.granularity)
    }
}
